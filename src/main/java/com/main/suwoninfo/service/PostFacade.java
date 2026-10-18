package com.main.suwoninfo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.main.suwoninfo.domain.Post;
import com.main.suwoninfo.dto.PostResponse;
import com.main.suwoninfo.exception.CustomException;
import com.main.suwoninfo.exception.PostErrorCode;
import com.main.suwoninfo.utils.RedisUtils;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;


@Service
@RequiredArgsConstructor
@Slf4j
public class PostFacade {

    private static final String CACHE_VERSION = "v1";
    private static final Duration POST_TTL = Duration.ofHours(1);
    private static final Duration IDS_TTL = Duration.ofMinutes(2);

    private final PostService postService;
    private final RedisUtils redisUtils;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    /**
     * 게시글 목록을 페이징 조회한다
     *
     * <p>대용량 데이터 환경에서 딥 페이징 성능 저하를 방지하기 위해
     * Redis 캐시 이정표를 활용하며, 장애 시 DB 직접 조회로 전환됩니다.</p>
     *
     * <h4>동작 순서</h4>
     * <ol>
     *     <li>요청된 페이지에 해당하는 이정표 커서(100페이지 단위) 계산</li>
     *     <li>실시간 추가/삭제 보정값을 Redis INCR/ZSET에서 조회</li>
     *     <li>Redis에서 ID 목록 + 개별 게시글 캐시 조회</li>
     *     <li>하나라도 없으면 재구성({@link #rebuildFind})</li>
     * </ol>
     *
     * @param limit     한 페이지에 가져올 게시글 수 ( 기본 값 10 )
     * @param page      요청 페이지 번호 ( 1에서 시작 )
     * @param postType  게시글 타입 ( TRADE, FREE 존재 )
     * @throws CustomException      Redis 장애 + 딥 페이징(10페이지 초과) 요청 시
     *                              ({@code PostErrorCode.TOO_DEEP_PAGE})
     * @apiNote CircuitBreaker 적용 - Redis 장애 시 {@link #handleRedisDown(int, int, Post.PostType, Throwable)}으로 fallback
     */
    @CircuitBreaker(name = "redisDown", fallbackMethod = "handleRedisDown")
    public List<PostResponse> findPostList(int limit, int page, Post.PostType postType) {

        // 마일스톤 인덱스, 좌측 값(1 ~ 100이면 1)
        int mileStoneIndex = ((page - 1) / 100) * 100 + 1;
        int newPostsCount = Integer.parseInt(redisUtils.get("new_" + postType + "_posts_count").toString());
        int deletedPostsCount = Math.toIntExact(redisUtils.zSetGetCount(String.format("deleted:post:ids:%s", postType),
                Long.valueOf(mileStoneIndex - 100), Long.valueOf(mileStoneIndex)));

        int pageId = Integer.parseInt(redisUtils.stringGet(postType + "_page:" + mileStoneIndex));
        int pagingOffset = (page - mileStoneIndex) * 10;

        // 삭제된 포스트 갯수와 증가된 포스트 갯수를 보정한 계산된 오프셋 ( 실제 오프셋 값 )
        int calculatedOffset = pagingOffset - newPostsCount + deletedPostsCount;

        // 마일스톤 인덱스가 1보다 큰 값이고 계산된 오프셋이 0 이하인 경우, 계산된 오프셋이 - 이므로 삭제된 포스트가 증가된 포스트보다 많음
        if(calculatedOffset < 0 && mileStoneIndex > 1) {
            mileStoneIndex -= 100;
            pageId = Integer.parseInt(redisUtils.stringGet(postType + "_page:" + mileStoneIndex));
            deletedPostsCount = Math.toIntExact(redisUtils.zSetGetCount(String.format("deleted:post:ids:%s", postType),
                    Long.valueOf(mileStoneIndex - 100), Long.valueOf(mileStoneIndex)));
            pagingOffset = (page - mileStoneIndex) * 10;
            calculatedOffset = pagingOffset - newPostsCount + deletedPostsCount;
        }

        // 마일스톤 인덱스가 1보다 작거나 같고 계산된 오프셋이 0 이하일 경우 ( 마일스톤 불일치 ) 캐싱하지 않고 오프셋 기반 탐색
        if (calculatedOffset < 0 && mileStoneIndex <= 1) {
            int absoluteOffset = (page - 1) * 10;
            return postService.findAbsolutePaging(limit, postType, absoluteOffset);
        }

        String idsKey = buildVersionedIdsKey(postType, mileStoneIndex, pagingOffset, limit);
        List<String> ids = redisUtils.listSet(idsKey, 0, -1);

        if (ids != null && !ids.isEmpty()) {
            List<Object> cached = redisUtils.multiGet(ids.stream().map(this::buildVersionedPostKey).toList());

            // 게시글이 수정/삭제되어 개별 캐시가 하나라도 비었으면 재구성
            if (cached != null && cached.stream().allMatch(Objects::nonNull)) {
                recordCacheAttempt(postType, "hit");
                return cached.stream()
                        .map(obj -> objectMapper.convertValue(obj, PostResponse.class))
                        .toList();
            }
        }

        recordCacheAttempt(postType, "miss");
        return rebuildFind(limit, pageId, idsKey, postType, pagingOffset);
    }

    /**
     * DB에서 조회 후 개별 게시글과 ID 목록을 다시 캐싱
     */
    private List<PostResponse> rebuildFind(int limit, int pageId, String idsKey, Post.PostType type, int pagingOffset) {
        List<PostResponse> postResponses = postService.findByPaging(limit, pageId, type, pagingOffset);

        redisUtils.delete(idsKey);
        if (postResponses.isEmpty()) {
            return postResponses;
        }

        Map<String, Object> toCache = new HashMap<>();
        for (PostResponse post : postResponses) {
            toCache.put(buildVersionedPostKey(String.valueOf(post.postId())), post);
        }
        redisUtils.pipelineSet(toCache, POST_TTL);

        List<String> ids = postResponses.stream()
                .map(d -> String.valueOf(d.postId()))
                .toList();
        redisUtils.listRightPush(idsKey, ids);
        redisUtils.expire(idsKey, IDS_TTL);

        return postResponses;
    }

    /**
     * Redis 서버 다운시
     * @param limit 가져올 게시글 갯수
     * @param page 요청 페이지 번호 ( 1에서 시작, findPostList와 동일 )
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
        return postService.findAbsolutePaging(limit, postType, (page - 1) * limit);
    }

    // 같은 이정표(100페이지) 안에서도 페이지마다 조회 결과가 다르므로 pagingOffset까지 키에 포함
    private String buildVersionedIdsKey(Post.PostType type, int mileStoneIndex, int pagingOffset, int limit) {
        return redisUtils.versionedKey(CACHE_VERSION,
                String.format("posts:ids:%s:milestone:%d:offset:%d:size:%d", type, mileStoneIndex, pagingOffset, limit));
    }

    private String buildVersionedPostKey(String postId) {
        return redisUtils.versionedKey(CACHE_VERSION, "post:" + postId);
    }

    private void recordCacheAttempt(Post.PostType type, String result) {
        meterRegistry.counter("cache.attempt",
                "type", type.toString(),
                "result", result
        ).increment();
    }
}
