package com.main.suwoninfo.service;

import com.main.suwoninfo.domain.*;
import com.main.suwoninfo.dto.PostRequest;
import com.main.suwoninfo.dto.PostResponse;
import com.main.suwoninfo.exception.CustomException;
import com.main.suwoninfo.exception.PostErrorCode;
import com.main.suwoninfo.exception.UserErrorCode;
import com.main.suwoninfo.idempotent.Idempotent;
import com.main.suwoninfo.repository.PostRepository;
import com.main.suwoninfo.repository.PostStatisticsRepository;
import com.main.suwoninfo.repository.UserRepository;
import com.main.suwoninfo.utils.RedisUtils;
import com.main.suwoninfo.utils.ToUtils;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.*;

import static com.main.suwoninfo.utils.ToUtils.toPostResponse;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PostStatisticsRepository postStatisticsRepository;

    private static final Duration COUNT_TTL = Duration.ofMinutes(5);
    private final RedisUtils redisUtils;



    @Transactional
    @Idempotent(key = "#userId")
    public PostResponse post(Long userId, PostRequest postReq) {

        User user = userRepository.findById(userId).orElseThrow(() -> new CustomException(UserErrorCode.NOT_AVAILABLE_EMAIL));
        Post post = Post.builder()
                .postType(postReq.postType())
                .title(postReq.content())
                .price(postReq.price())
                .tradeStatus(postReq.tradeStatus())
                .content(postReq.content())
                .tradeStatus(postReq.tradeStatus())
                .build();
        post.setUser(user);
        postRepository.post(post);
        postStatisticsRepository.findByType(postReq.postType()).addCount();
        return toPostResponse(post);
    }

    @Transactional
    public void update(Long postId, Long userId, PostResponse postDto) {

        Post findPost = postRepository.findById(postId).orElseThrow(() -> new CustomException(PostErrorCode.NOT_EXIST_POST));
        User findUser = userRepository.findById(userId).orElseThrow(() -> new CustomException(UserErrorCode.NOT_AVAILABLE_EMAIL));
        if (findPost.getUser() != findUser)
            throw new CustomException(PostErrorCode.NOT_EQUAL_USER);

        findPost.update(postDto);
    }

    @Transactional
    public void delete(Long postId, String email) {

        Post post = findById(postId);
        User user = userRepository.findByEmail(email).orElseThrow(() -> new CustomException(UserErrorCode.NOT_EXIST_EMAIL));
        if (!Objects.equals(post.getUser().getId(), user.getId()))
            throw new CustomException(PostErrorCode.NOT_EQUAL_USER);
        postRepository.delete(post);
        for (int i = 0; i < post.getComment().size(); i++) {
            post.getComment().get(i).setActivated(false);
        }
    }

    public Post findById(Long id) {
        return postRepository.findById(id).orElseThrow(() -> new CustomException(PostErrorCode.NOT_EXIST_POST));
    }

    @CircuitBreaker(name = "redisLock", fallbackMethod = "countFallback")
    public int countPost(Post.PostType postType) {

        String cacheKey = "posts:" + postType + ":count";
        Object cached = redisUtils.get(cacheKey);

        if (cached != null) {
            return Integer.parseInt(cached.toString());
        }

        int count = postStatisticsRepository.countPost(postType);
        redisUtils.stringSet(cacheKey, String.valueOf(count), Duration.ofMinutes(5));

        return count;
    }

    public int countFallback(Post.PostType postType, Throwable t) {
        log.error("CircuitBreaker Open Fallback 실행. 원인: {}", t.getMessage());
        return postStatisticsRepository.countPost(postType);
    }

    public List<PostResponse> searchPost(String keyword, int limit, int offset, Post.PostType postType) {
        List<Post> postList = postRepository.findByTitle(keyword, limit, offset, postType);
        return postList.stream().map(ToUtils::toPostResponse).toList();
    }

    public List<Post> findAllById(List<Long> longIds) {
        return postRepository.findAllById(longIds);
    }

    public List<Post> findByPaging(int limit, int offset, Post.PostType postType) {
        return  postRepository.findByPaging(limit, offset, postType);
    }
}



