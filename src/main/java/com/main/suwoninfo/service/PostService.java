package com.main.suwoninfo.service;

import com.main.suwoninfo.domain.*;
import com.main.suwoninfo.dto.PostRequest;
import com.main.suwoninfo.dto.PostResponse;
import com.main.suwoninfo.exception.CustomException;
import com.main.suwoninfo.exception.PostErrorCode;
import com.main.suwoninfo.exception.UserErrorCode;
import com.main.suwoninfo.repository.*;
import com.main.suwoninfo.utils.RedisUtils;
import com.main.suwoninfo.utils.ToUtils;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static com.main.suwoninfo.utils.ToUtils.toPostResponse;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final PostQueryRepository postQueryRepository;
    private final UserRepository userRepository;
    private final PostStatisticsRepository postStatisticsRepository;
    private final PostStatisticsQueryRepository postStatisticsQueryRepository;

    private static final String CACHE_VERSION = "v1";
    private final RedisUtils redisUtils;


    @Transactional
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
        postRepository.save(post);
        PostStatistics postStatistics = postStatisticsRepository.findByPostType(postReq.postType());
        postStatistics.addCount();
        redisUtils.increment("new_" + post.getPostType() + "_posts_count");
        redisUtils.delete(countCacheKey(post.getPostType()));
        return toPostResponse(post);
    }

    @Transactional
    public void update(Long postId, Long userId, PostResponse postDto) {

        Post findPost = postRepository.findById(postId).orElseThrow(() -> new CustomException(PostErrorCode.NOT_EXIST_POST));
        User findUser = userRepository.findById(userId).orElseThrow(() -> new CustomException(UserErrorCode.NOT_AVAILABLE_EMAIL));
        if (findPost.getUsers() != findUser)
            throw new CustomException(PostErrorCode.NOT_EQUAL_USER);

        findPost.update(postDto);
        redisUtils.delete(postCacheKey(postId));
    }

    @Transactional
    public void delete(Long postId, String email) {

        Post post = findById(postId);
        User user = userRepository.findByEmail(email).orElseThrow(() -> new CustomException(UserErrorCode.NOT_EXIST_EMAIL));

        if (!Objects.equals(post.getUsers().getId(), user.getId()))
            throw new CustomException(PostErrorCode.NOT_EQUAL_USER);

        PostStatistics postStatistics = postStatisticsRepository.findByPostType(post.getPostType());
        postStatistics.minusCount();

        redisUtils.zSetSet("deleted:post:ids:" + post.getPostType(), postId);
        postRepository.delete(post);
        redisUtils.delete(countCacheKey(post.getPostType()));
        redisUtils.delete(postCacheKey(postId));
    }

    public Post findById(Long id) {
        return postRepository.findById(id).orElseThrow(() -> new CustomException(PostErrorCode.NOT_EXIST_POST));
    }

    // 게시글 상세 조회: photo가 LAZY이므로 트랜잭션 안에서 DTO로 변환해서 반환
    public PostResponse view(Long id) {
        return toPostResponse(findById(id));
    }

    @Transactional
    public int countPost(Post.PostType postType) {

        String cacheKey = countCacheKey(postType);

        try {
            String cached = redisUtils.stringGet(cacheKey);
            if (cached != null) {
                return Integer.parseInt(cached);
            }
        } catch (DataAccessException e) {
            // Redis 장애 시 목록 조회 전체가 실패하지 않도록 DB 직접 조회
            log.warn("Redis 장애로 게시글 수 DB 직접 조회: {}", e.getMessage());
            return postStatisticsQueryRepository.countPost(postType);
        }

        int count = postStatisticsQueryRepository.countPost(postType);
        redisUtils.stringSet(cacheKey, String.valueOf(count), Duration.ofMinutes(5));

        return count;
    }

    private String countCacheKey(Post.PostType postType) {
        return redisUtils.versionedKey(CACHE_VERSION, "posts:" + postType + ":count");
    }

    // PostFacade가 목록 조회 시 캐싱하는 개별 게시글 키
    private String postCacheKey(Long postId) {
        return redisUtils.versionedKey(CACHE_VERSION, "post:" + postId);
    }

    public List<PostResponse> searchPost(String keyword, int limit, int offset, Post.PostType postType) {
        List<Post> postList = postQueryRepository.findByTitle(keyword, limit, offset, postType);
        return postList.stream().map(ToUtils::toPostResponse).toList();
    }

    // photo가 LAZY이므로 트랜잭션 안에서 DTO로 변환해서 반환
    public List<PostResponse> findAllById(List<Long> longIds) {
        return postRepository.findAllById(longIds).stream().map(ToUtils::toPostResponse).toList();
    }

    public List<PostResponse> findByPaging(int limit, int mileStoneOffset, Post.PostType postType, int pagingOffset) {
        log.info("limit {}, mileStoneOffset {}, postType {}, pagingOffset {}", limit, mileStoneOffset, postType, pagingOffset);

        return postQueryRepository.findByCursorPaging(limit, mileStoneOffset, postType, pagingOffset).stream().map(ToUtils::toPostResponse).collect(Collectors.toList());
    }

    public List<PostResponse> findAbsolutePaging(int limit, Post.PostType postType, int absoluteOffset) {

        return postQueryRepository.findAbsolutePaging(limit, postType, absoluteOffset).stream().map(ToUtils::toPostResponse).collect(Collectors.toList());
    }
}



