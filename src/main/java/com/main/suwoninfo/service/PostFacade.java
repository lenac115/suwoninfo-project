package com.main.suwoninfo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.main.suwoninfo.domain.Post;
import com.main.suwoninfo.dto.PostResponse;
import com.main.suwoninfo.exception.CustomException;
import com.main.suwoninfo.exception.PostErrorCode;
import com.main.suwoninfo.repository.LockRepository;
import com.main.suwoninfo.utils.RedisUtils;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
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
    private static final double PER_DELTA = 0.2; // TTL의 20% 남았을 때부터 확률적 갱신
    private static final double PER_BETA = 1.0; // 갱신 확률 조절

    private final PostService postService;
    private final RedisUtils redisUtils;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @Autowired
    @Lazy
    private PostFacade self;

    /**
     * 게시글 목록 조회 (메인 메서드)
     */
    @CircuitBreaker(name = "redisDown", fallbackMethod = "handleRedisDown")
    public List<PostResponse> findPostList(int limit, int page, Post.PostType postType) throws InterruptedException {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            int mileStoneIndex = ((page - 1) / 100) * 100 + 1;
            int newPostsCount = Integer.parseInt(redisUtils.get("new_" + postType + "_posts_count").toString());
            int deletedPostsCount = Math.toIntExact(redisUtils.zSetGetCount(String.format("deleted:post:ids:%s", postType),
                    Long.valueOf(mileStoneIndex - 100), Long.valueOf(mileStoneIndex)));

            int pageId = Integer.parseInt(redisUtils.stringGet(postType + "_page:" + mileStoneIndex));
            int pagingOffset = (page - mileStoneIndex) * 10;
            int calculatedOffset = pagingOffset - newPostsCount + deletedPostsCount;

            if(calculatedOffset < 0 && mileStoneIndex > 1) {
                mileStoneIndex -= 100;
                pageId = Integer.parseInt(redisUtils.stringGet(postType + "_page:" + mileStoneIndex));
                deletedPostsCount = Math.toIntExact(redisUtils.zSetGetCount(String.format("deleted:post:ids:%s", postType),
                        Long.valueOf(mileStoneIndex - 100), Long.valueOf(mileStoneIndex)));
                pagingOffset = (page - mileStoneIndex) * 10;
                calculatedOffset = pagingOffset - newPostsCount + deletedPostsCount;
            }

            if (calculatedOffset < 0 && mileStoneIndex <= 1) {
                int absoluteOffset = (page - 1) * 10;
                return postService.findAbsolutePaging(limit, postType, absoluteOffset);
            }

            String idsKey = buildVersionedIdsKey(postType, mileStoneIndex, limit);

            List<String> idStrs = redisUtils.listSet(idsKey, 0, -1);

            if (idStrs != null && !idStrs.isEmpty()) {
                recordCacheAttempt(postType, "ids_hit");

                // PER: TTL이 짧게 남았으면 백그라운드 갱신
                Long ttl = redisUtils.getTtl(idsKey);
                if (shouldRefreshEarly(ttl)) {
                    log.info("PER 트리거 : TTL={}초, 백그라운드 캐시 갱신 시작", ttl);
                    self.asyncRebuild(limit, pageId, idsKey, postType, pagingOffset);
                }

                return handleCachedIds(idStrs, limit, pageId, idsKey, postType, pagingOffset, mileStoneIndex);
            }

            // Cache miss: 동기 재구성
            recordCacheAttempt(postType, "ids_miss");
            return self.rebuildFindWithLock(limit, pageId, idsKey, postType, pagingOffset);

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
                                               String idsKey, Post.PostType postType, int pagingOffset, int mileStoneIndex) throws InterruptedException {
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
            return self.rebuildFindWithLock(limit, pageId, idsKey, postType, pagingOffset);
        }

        // 소수 누락: 보충
        if (missingIds.size() <= MISSING_THRESHOLD) {
            recordCacheAttempt(postType, "partial_hit");
            return repairMissingPosts(idStrs, missingIds, idsKey, limit, pageId, postType, pagingOffset, mileStoneIndex);
        }

        // 다수 누락: 재구성
        recordCacheAttempt(postType, "too_many_missing");
        return self.rebuildFindWithLock(limit, pageId, idsKey, postType, pagingOffset);
    }

    /**
     * 누락된 게시글 보충
     */
    private List<PostResponse> repairMissingPosts(List<String> allIds, List<String> missingIds,
                                                  String idsKey, int limit, int pageId,
                                                  Post.PostType postType, int pagingOffset, int mileStoneIndex) throws InterruptedException {
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
                return self.rebuildFindWithLock(limit, pageId, idsKey, postType, pagingOffset);
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
    public List<PostResponse> rebuildFindWithLock(int limit, int pageId, String idsKey, Post.PostType type, int pagingOffset) {
        Timer.Sample sample = Timer.start(meterRegistry);

        //String lockKey = "cache:rebuild:" + type + ":page:" + mileStoneOffset;

        try {
            log.info("캐시 재구성 시작: type={}, limit={}, offset={}", type, limit, pageId + pagingOffset);

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

            // DB 조회
            List<PostResponse> postResponses = postService.findByPaging(limit, pageId, type, pagingOffset);

            // 개별 게시글 캐싱
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
        }
    }

    /**
     * 비동기 캐시 갱신 (PER용)
     */
    @Async
    public void asyncRebuild(int limit, int offset, String idsKey, Post.PostType type, int pagingOffset) {
        try {
            self.rebuildFindWithLock(limit, offset, idsKey, type, pagingOffset);
            recordCacheAttempt(type, "per_refresh_success");
        } catch (Exception e) {
            log.warn("비동기 캐시 갱신 실패: type={}, offset={}", type, offset, e);
            recordCacheAttempt(type, "per_refresh_fail");
        }
    }


    /**
     * Redis 서버 다운시
     * @param limit 가져올 게시글 갯수
     * @param page 통짜 offset
     * @param postType 게시글 타입
     * @param throwable 서킷 브레이커에 필수로 들어가야하는 인수
     * @return 페이지 리턴
     */
    protected List<PostResponse> handleRedisDown(int limit, int page, Post.PostType postType, Throwable throwable) {

        if (page > 10) {
            log.warn("Redis 장애 상황에서 깊은 페이징 요청 차단: page={}", page);
            throw new CustomException(PostErrorCode.TOO_DEEP_PAGE);
        }

        log.warn("Redis 서버 응답 실패. DB 직접 조회로 fallback");
        return postService.findAbsolutePaging(limit, postType, page * 10);
    }

    /**
     * PER_DELTA 남은 TTL 시간(백분율), PER_BETA 갱신 확률(백분율)
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

    /**
     * 캐시 TTL에 무작위성 추가
     * @param baseTtl 원본 캐시 TTL
     * @return 원본에서 -10% ~ 10% 차이 나게
     */
    private Duration addJitter(Duration baseTtl) {
        long seconds = baseTtl.getSeconds();
        long jitter = ThreadLocalRandom.current().nextLong(-seconds / 10, seconds / 10);
        return Duration.ofSeconds(Math.max(1, seconds + jitter));
    }

    /**
     * 캐시 동작 기록
     * @param type 게시글 타입
     * @param result 캐시 메소드 동작 결과
     */
    private void recordCacheAttempt(Post.PostType type, String result) {
        meterRegistry.counter("cache.attempt",
                "type", type.toString(),
                "result", result
        ).increment();
    }
}
