package com.main.suwoninfo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.main.suwoninfo.domain.Post;
import com.main.suwoninfo.dto.PostResponse;
import com.main.suwoninfo.repository.LockRepository;
import com.main.suwoninfo.utils.RedisUtils;
import com.main.suwoninfo.utils.ToUtils;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.main.suwoninfo.utils.ToUtils.toPostResponse;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostFacade {

    private static final String CACHE_VERSION = "v1";
    private static final Duration POST_TTL = Duration.ofHours(1);
    private static final Duration IDS_TTL = Duration.ofMinutes(2);
    private static final int MISSING_THRESHOLD = 3;
    private static final double PER_DELTA = 0.5; // TTL의 50% 남았을 때부터 확률적 갱신
    private static final double PER_BETA = 1.0;  // 갱신 확률 조절

    private final PostService postService;
    private final RedisUtils redisUtils;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final LockRepository lockRepository;


    @Autowired
    @Lazy
    private PostFacade self;

    /**
     * 게시글 목록 조회 (메인 메서드)
     */
    public List<PostResponse> findPostList(int limit, int offset, Post.PostType postType) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            //int pageIndex = offset / Math.max(limit, 1);
            int mileStoneIndex = ((offset - 1) / 100) * 100 + 1;

            int pageId = Integer.parseInt(redisUtils.stringGet(postType + "_page:" + mileStoneIndex));
            int pagingOffset = (offset - mileStoneIndex) * 10;
            String idsKey = buildVersionedIdsKey(postType, mileStoneIndex, limit);

            List<String> idStrs = redisUtils.listSet(idsKey, 0, -1);

            if (idStrs != null && !idStrs.isEmpty()) {
                recordCacheAttempt(postType, "ids_hit");

                // PER: TTL이 짧게 남았으면 백그라운드 갱신
                Long ttl = redisUtils.getTtl(idsKey);
                if (shouldRefreshEarly(ttl)) {
                    log.info("PER 트리거: TTL={}초, 백그라운드 캐시 갱신 시작", ttl);
                    self.asyncRebuild(limit, pageId, idsKey, postType, pagingOffset, mileStoneIndex);
                }

                return handleCachedIds(idStrs, limit, pageId, idsKey, postType, pagingOffset, mileStoneIndex);
            }

            // Cache miss: 동기 재구성
            recordCacheAttempt(postType, "ids_miss");
            return self.rebuildFindWithLock(limit, pageId, idsKey, postType, pagingOffset, mileStoneIndex);

        } finally {
            sample.stop(Timer.builder("api.posts.list")
                    .tag("type", postType.toString())
                    .register(meterRegistry));
        }
    }

    /**
     * 캐시된 ID 목록 처리
     */
    private List<PostResponse> handleCachedIds(List<String> idStrs, int limit, int pageId,
                                               String idsKey, Post.PostType postType, int pagingOffset, int mileStoneIndex) {
        List<String> postKeys = idStrs.stream()
                .map(this::buildVersionedPostKey)
                .collect(Collectors.toList());

        List<Object> cached = redisUtils.multiGet(postKeys);

        // 완전 캐시 히트
        if (cached != null && cached.stream().allMatch(Objects::nonNull)) {
            recordCacheAttempt(postType, "full_hit");
            return cached.stream()
                    .map(obj -> objectMapper.convertValue(obj, PostResponse.class))
                    .collect(Collectors.toList());
        }

        // 일부 누락
        List<String> missingIds = findMissingIds(idStrs, cached);

        if (missingIds.isEmpty()) {
            log.warn("캐시 데이터 불일치 감지. 재구성 필요: {}", idsKey);
            return self.rebuildFindWithLock(limit, pageId, idsKey, postType, pagingOffset, mileStoneIndex);
        }

        // 소수 누락: 보충
        if (missingIds.size() <= MISSING_THRESHOLD) {
            recordCacheAttempt(postType, "partial_hit");
            return repairMissingPosts(idStrs, missingIds, idsKey, limit, pageId, postType, pagingOffset, mileStoneIndex);
        }

        // 다수 누락: 재구성
        recordCacheAttempt(postType, "too_many_missing");
        return self.rebuildFindWithLock(limit, pageId, idsKey, postType, pagingOffset, mileStoneIndex);
    }

    /**
     * 누락된 게시글 보충
     */
    private List<PostResponse> repairMissingPosts(List<String> allIds, List<String> missingIds,
                                                  String idsKey, int limit, int pageId,
                                                  Post.PostType postType, int pagingOffset, int mileStoneIndex) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            List<Long> longIds = missingIds.stream()
                    .map(Long::parseLong)
                    .collect(Collectors.toList());

            List<Post> missingPosts = postService.findAllById(longIds);

            // 찾은 게시글 수가 요청한 ID 수보다 적으면 삭제된 게시글 존재 → 재구성
            if (missingPosts.size() < longIds.size()) {
                log.warn("삭제된 게시글 감지. 캐시 재구성: {}", idsKey);
                redisUtils.expire(idsKey, Duration.ofSeconds(10));
                return self.rebuildFindWithLock(limit, pageId, idsKey, postType, pagingOffset, mileStoneIndex);
            }

            // 누락된 게시글만 캐싱 (파이프라인 사용)
            Map<String, Object> toCache = new HashMap<>();
            for (Post post : missingPosts) {
                PostResponse response = toPostResponse(post);
                toCache.put(buildVersionedPostKey(String.valueOf(post.getId())), response);
            }
            redisUtils.pipelineSet(toCache, addJitter(POST_TTL));

            // 전체 결과 조립
            Map<Long, Post> postMap = missingPosts.stream()
                    .collect(Collectors.toMap(Post::getId, Function.identity()));

            List<PostResponse> result = new ArrayList<>();
            for (String id : allIds) {
                Object cachedObj = redisUtils.get(buildVersionedPostKey(id));

                if (cachedObj != null) {
                    result.add(objectMapper.convertValue(cachedObj, PostResponse.class));
                } else {
                    Post post = postMap.get(Long.valueOf(id));
                    if (post != null) {
                        result.add(toPostResponse(post));
                    }
                }
            }

            return result;

        } finally {
            sample.stop(Timer.builder("cache.repair")
                    .tag("type", postType.toString())
                    .register(meterRegistry));
        }
    }

    /**
     * 캐시 재구성
     */
    public List<PostResponse> rebuildFindWithLock(int limit, int pageId, String idsKey, Post.PostType type, int pagingOffset, int mileStoneOffset) {
        Timer.Sample sample = Timer.start(meterRegistry);

        String lockKey = "cache:rebuild:" + type + ":page:" + mileStoneOffset;

        try {
            log.info("캐시 재구성 시작: type={}, limit={}, offset={}", type, limit, pageId + pagingOffset);
            Boolean lockAcquired = lockRepository.getLock(lockKey, 30);

            if (!lockAcquired) {
                log.warn("락 획득 실패. 캐시된 데이터 반환 시도: {}", lockKey);
                // 락 획득 실패 시 기존 캐시 반환 또는 fallback
                return handleLockFailure(limit, pageId, idsKey, type, pagingOffset, mileStoneOffset);
            }


            // Double-check
            List<String> existing = redisUtils.listSet(idsKey, 0, -1);
            if (existing != null && !existing.isEmpty()) {
                List<Object> cached = redisUtils.multiGet(
                        existing.stream().map(this::buildVersionedPostKey).collect(Collectors.toList()));

                if (cached != null && cached.stream().allMatch(Objects::nonNull)) {
                    log.info("락 획득 후 캐시 발견. DB 조회 스킵");
                    recordCacheAttempt(type, "rebuild_skip");
                    return cached.stream()
                            .map(obj -> objectMapper.convertValue(obj, PostResponse.class))
                            .collect(Collectors.toList());
                }
            }

            // 카운트 캐시 무효화
            redisUtils.delete(buildVersionedKey("posts:" + type + ":count"));
            postService.countPost(type);

            // DB 조회 (버그 수정: type 파라미터 사용)
            List<PostResponse> postResponses = postService.findByPaging(limit, pageId, type, pagingOffset);

            // 개별 게시글 캐싱 (파이프라인 사용)
            Map<String, Object> toCache = new HashMap<>();
            for (PostResponse dto : postResponses) {
                toCache.put(buildVersionedPostKey(String.valueOf(dto.postId())), dto);
            }
            if (!toCache.isEmpty()) {
                redisUtils.pipelineSet(toCache, addJitter(POST_TTL));
            }

            // ID 목록 캐싱
            redisUtils.delete(idsKey);
            if (!postResponses.isEmpty()) {
                List<String> ids = postResponses.stream()
                        .map(d -> String.valueOf(d.postId()))
                        .collect(Collectors.toList());
                redisUtils.listRightPush(idsKey, ids);
                redisUtils.expire(idsKey, addJitter(IDS_TTL));
            } else {
                redisUtils.set(idsKey + ":empty", "1", Duration.ofSeconds(30));
            }

            log.info("캐시 재구성 완료: {} 건", postResponses.size());
            recordCacheAttempt(type, "rebuild_success");
            return postResponses;

        } catch (Exception e) {
            log.error("캐시 재구성 중 오류 발생", e);
            throw e;
        } finally {
            Boolean released = lockRepository.releaseLock(lockKey);
            if (released) {
                log.info("네임드 락 해제 성공: {}", lockKey);
            } else {
                log.warn("네임드 락 해제 실패: {}", lockKey);
            }
        }
    }

    /**
     * 비동기 캐시 갱신 (PER용)
     */
    @Async
    public void asyncRebuild(int limit, int offset, String idsKey, Post.PostType type, int pagingOffset, int mileStoneIndex) {
        try {
            self.rebuildFindWithLock(limit, offset, idsKey, type, pagingOffset, mileStoneIndex);
            recordCacheAttempt(type, "per_refresh_success");
        } catch (Exception e) {
            log.warn("비동기 캐시 갱신 실패: type={}, offset={}", type, offset, e);
            recordCacheAttempt(type, "per_refresh_fail");
        }
    }


    // 락 획득 실패 시 처리
    private List<PostResponse> handleLockFailure(int limit, int offset, String idsKey, Post.PostType type, int pagingOffset, int mileStoneOffset) {
        // 잠시 대기 후 캐시 재확인
        try {
            Thread.sleep(100);
            List<String> ids = redisUtils.listSet(idsKey, 0, -1);
            if (ids != null && !ids.isEmpty()) {
                return handleCachedIds(ids, limit, offset, idsKey, type, pagingOffset, mileStoneOffset);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 캐시가 여전히 없으면 DB 직접 조회
        log.warn("락 대기 타임아웃. DB 직접 조회로 fallback");
        return postService.findByPaging(limit, offset, type, pagingOffset);
    }


    /*public List<PostResponse> findFallback(int limit, int offset, Post.PostType type, Throwable t) {

        log.error("CircuitBreaker Open Fallback 실행. 원인: {}", t.getMessage());

        try {
            return postService.findByPaging(limit, offset, type).stream().map(ToUtils::toPostResponse).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("DB 연결 실패. 원인: {}", e.getMessage());
            return Collections.emptyList();
        }
    }*/

    /**
     * Probabilistic Early Refresh 판단
     * XFetch 알고리즘 기반
     */
    private boolean shouldRefreshEarly(Long ttl) {
        if (ttl == null || ttl <= 0) {
            return false;
        }

        double delta = PostFacade.IDS_TTL.getSeconds() * PER_DELTA;
        double threshold = delta * PER_BETA * Math.log(ThreadLocalRandom.current().nextDouble());

        boolean shouldRefresh = ttl < threshold;

        if (shouldRefresh) {
            log.debug("PER 조건 충족: ttl={}, threshold={}", ttl, threshold);
        }

        return shouldRefresh;
    }

    private List<String> findMissingIds(List<String> allIds, List<Object> cached) {
        List<String> missing = new ArrayList<>();
        for (int i = 0; i < cached.size(); i++) {
            if (cached.get(i) == null) {
                missing.add(allIds.get(i));
            }
        }
        return missing;
    }

    private String buildVersionedIdsKey(Post.PostType type, int mileStoneIndex, int limit) {
        return buildVersionedKey(
                String.format("posts:ids:%s:page:%d:size:%d", type, mileStoneIndex, limit)
        );
    }

    private String buildVersionedPostKey(String postId) {
        return buildVersionedKey("post:" + postId);
    }

    private String buildVersionedKey(String key) {
        return redisUtils.versionedKey(CACHE_VERSION, key);
    }

    private Duration addJitter(Duration baseTTL) {
        long seconds = baseTTL.getSeconds();
        long jitter = ThreadLocalRandom.current().nextLong(-seconds / 10, seconds / 10);
        return Duration.ofSeconds(Math.max(1, seconds + jitter));
    }

    private void recordCacheAttempt(Post.PostType type, String result) {
        meterRegistry.counter("cache.attempt",
                "type", type.toString(),
                "result", result
        ).increment();
    }
}
