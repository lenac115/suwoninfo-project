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
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
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
    private final UserRepository userRepository;
    private final PostStatisticsRepository postStatisticsRepository;

    private static final String CACHE_VERSION = "v1";
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
        PostStatistics postStatistics = postStatisticsRepository.findByType(postReq.postType());
        postStatistics.addCount();
        redisUtils.increment("new_" + post.getPostType() + "_posts_count");
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
        PostStatistics postStatistics = postStatisticsRepository.findByType(post.getPostType());
        postStatistics.minusCount();
        redisUtils.zSetSet("deleted:post:ids:" + post.getPostType(), postId);
        postRepository.delete(post);
        for (int i = 0; i < post.getComment().size(); i++) {
            post.getComment().get(i).setActivated(false);
        }
    }

    public Post findById(Long id) {
        return postRepository.findById(id).orElseThrow(() -> new CustomException(PostErrorCode.NOT_EXIST_POST));
    }

    @Transactional
    public int countPost(Post.PostType postType) {

        String cacheKey = "posts:" + postType + ":count";
        Object cached = redisUtils.get(redisUtils.versionedKey(CACHE_VERSION, cacheKey));

        if (cached != null) {
            return Integer.parseInt(cached.toString());
        }

        int count = postStatisticsRepository.countPost(postType);
        redisUtils.stringSet(cacheKey, String.valueOf(count), Duration.ofMinutes(5));

        return count;
    }

    public List<PostResponse> searchPost(String keyword, int limit, int offset, Post.PostType postType) {
        List<Post> postList = postRepository.findByTitle(keyword, limit, offset, postType);
        return postList.stream().map(ToUtils::toPostResponse).toList();
    }

    public List<Post> findAllById(List<Long> longIds) {
        return postRepository.findAllById(longIds);
    }

    public List<PostResponse> findByPaging(int limit, int mileStoneOffset, Post.PostType postType, int pagingOffset) {
        log.info("limit {}, mileStoneOffset {}, postType {}, pagingOffset {}", limit, mileStoneOffset, postType, pagingOffset);

        return postRepository.findByCursorPaging(limit, mileStoneOffset, postType, pagingOffset).stream().map(ToUtils::toPostResponse).collect(Collectors.toList());
    }

    public List<PostResponse> findAbsolutePaging(int limit, Post.PostType postType, int absoluteOffset) {

        return postRepository.findAbsolutePaging(limit, postType, absoluteOffset).stream().map(ToUtils::toPostResponse).collect(Collectors.toList());
    }
}



