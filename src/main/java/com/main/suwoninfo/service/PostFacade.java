package com.main.suwoninfo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.main.suwoninfo.domain.Post;
import com.main.suwoninfo.dto.PostResponse;
import com.main.suwoninfo.lock.DistributedLock;
import com.main.suwoninfo.utils.RedisUtils;
import com.main.suwoninfo.utils.ToUtils;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.main.suwoninfo.utils.ToUtils.toPostResponse;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostFacade {
    private final PostService postService;
    private final RedisUtils redisUtils;
    private final ObjectMapper objectMapper;

    @Autowired
    @Lazy
    private PostFacade self;

    private static final Duration POST_TTL = Duration.ofHours(1);
    private static final Duration IDS_TTL = Duration.ofMinutes(2);

    @CircuitBreaker(name = "redisLock", fallbackMethod = "findFallback")
    public List<PostResponse> findPostList(int limit, int offset, Post.PostType postType) {
        int pageIndex = offset / Math.max(limit, 1);
        String idsKey = "posts:ids:" + postType + ":page:" + pageIndex + ":size:" + limit;

        List<String> idStrs = redisUtils.listSet(idsKey, 0, -1);

        if (idStrs != null && !idStrs.isEmpty()) {

            List<String> postKeys = idStrs.stream().map(id -> "post:" + id).toList();
            List<Object> cached = redisUtils.multiGet(postKeys);

            // cache hit
            if (cached != null && cached.stream().allMatch(Objects::nonNull)) {
                return cached.stream()
                        .map(obj -> objectMapper.convertValue(obj, PostResponse.class))
                        .collect(Collectors.toList());
            }

            // cache miss시 일부 누락이면 보충 or 재빌드
            if (cached != null && cached.stream().allMatch(Objects::nonNull)) {
                return (List) cached;
            }

            List<String> missingIds = new ArrayList<>();
            for (int i = 0; i < cached.size(); i++) {
                if (cached.get(i) == null) missingIds.add(idStrs.get(i));
            }

            if (!missingIds.isEmpty() && missingIds.size() <= 3) {
                List<Long> longIds = missingIds.stream().map(Long::parseLong).toList();
                List<Post> missingPosts = postService.findAllById(longIds);
                if (longIds.size() > missingPosts.size()) {
                    redisUtils.expire(idsKey, Duration.ofSeconds(10));
                    return self.rebuildFindWithLock(limit, offset, idsKey, postType)
                            .stream()
                            .map(obj -> objectMapper.convertValue(obj, PostResponse.class)).toList();
                }
                Map<Long, Post> idMap = missingPosts.stream().collect(Collectors.toMap(Post::getId, Function.identity()));

                List<PostResponse> result = new ArrayList<>();

                for (String id : idStrs) {
                    Object cachedObj = redisUtils.get("post:" + id);

                    if (cachedObj != null) {
                        result.add(objectMapper.convertValue(cachedObj, PostResponse.class));
                    } else {
                        Post p = idMap.get(Long.valueOf(id));

                        if (p != null) {
                            PostResponse pw = toPostResponse(p);
                            redisUtils.set("post:" + id, pw, POST_TTL);
                            result.add(pw);
                        } else {
                            return self.rebuildFindWithLock(limit, offset, idsKey, postType)
                                    .stream()
                                    .map(obj -> objectMapper.convertValue(obj, PostResponse.class)).toList();
                        }
                    }
                }

                return result;
            }
            // 다수 누락 재빌드
            return self.rebuildFindWithLock(limit, offset, idsKey, postType)
                    .stream()
                    .map(obj -> objectMapper.convertValue(obj, PostResponse.class)).toList();
        }

        // idsKey null 재빌드
        return self.rebuildFindWithLock(limit, offset, idsKey, postType)
                .stream()
                .map(obj -> objectMapper.convertValue(obj, PostResponse.class)).toList();
    }

    @DistributedLock(key = "'posts:ids:' + #type + ':page:' + #offset / #limit + ':size:' + #limit")
    public List<PostResponse> rebuildFindWithLock(int limit, int offset, String idsKey, Post.PostType type) {

        System.out.println("진행중");
        // double-check
        List<String> existing = redisUtils.listSet(idsKey, 0, -1);
        if (existing != null && !existing.isEmpty()) {
            List<Object> cached = redisUtils.multiGet(
                    existing.stream().map(id -> "post:" + id).collect(Collectors.toList()));
            if (cached != null && cached.stream().allMatch(Objects::nonNull)) {
                log.info("캐시 발견, DB 조회 스킵하고 리턴합니다.");
                return cached.stream().map(obj -> objectMapper.convertValue(obj, PostResponse.class)).collect(Collectors.toList());
            }
        }

        redisUtils.delete("posts:" + type + ":count");
        postService.countPost(type);

        List<Post> posts;
        List<PostResponse> postResponses;

        // DB 조회
        posts = postService.findByPaging(limit, offset, Post.PostType.FREE);
        postResponses = posts.stream().map(ToUtils::toPostResponse).toList();
        // per-post 캐시 세팅
        for (PostResponse dto : postResponses) {
            redisUtils.set("post:" + dto.postId(), dto, POST_TTL);
        }

        // ids 리스트 저장
        redisUtils.delete(idsKey);
        if (!postResponses.isEmpty()) {
            List<String> ids = postResponses.stream().map(d -> String.valueOf(d.postId())).collect(Collectors.toList());
            redisUtils.listRightPush(idsKey, ids);
            redisUtils.expire(idsKey, IDS_TTL.plusSeconds(ThreadLocalRandom.current().nextLong(-10, 11)));
        } else {
            // 빈 결과도 짧게 캐시
            redisUtils.set(idsKey + ":empty", "1", Duration.ofSeconds(30));
        }
        return postResponses;
    }

    public List<PostResponse> findFallback(int limit, int offset, Post.PostType type, Throwable t) {

        log.error("CircuitBreaker Open Fallback 실행. 원인: {}", t.getMessage());

        return postService.findPostListTest(limit, offset, type);
    }
}
