package com.main.suwoninfo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.main.suwoninfo.domain.Post;
import com.main.suwoninfo.domain.PostType;
import com.main.suwoninfo.domain.TradeStatus;
import com.main.suwoninfo.domain.User;
import com.main.suwoninfo.dto.PhotoDto;
import com.main.suwoninfo.dto.PostDto;
import com.main.suwoninfo.form.PostWithId;
import com.main.suwoninfo.exception.CustomException;
import com.main.suwoninfo.exception.PostErrorCode;
import com.main.suwoninfo.exception.UserErrorCode;
import com.main.suwoninfo.form.PostWithIdAndPrice;
import com.main.suwoninfo.form.SearchFreeListForm;
import com.main.suwoninfo.form.SearchTradeListForm;
import com.main.suwoninfo.idempotent.Idempotent;
import com.main.suwoninfo.lock.DistributedLock;
import com.main.suwoninfo.repository.PostRepository;
import com.main.suwoninfo.repository.UserRepository;
import com.main.suwoninfo.utils.RedisUtils;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;

    private static final Duration POST_TTL = Duration.ofHours(1);
    private static final Duration IDS_TTL = Duration.ofMinutes(2);
    private static final Duration COUNT_TTL = Duration.ofMinutes(5);
    private final RedisUtils redisUtils;
    private final ObjectMapper objectMapper;


    @Transactional
    @Idempotent(user = "#userId", key = "#idemKey")
    public PostDto post(Long userId, PostDto postdto, String idemKey) {

        User user = userRepository.findById(userId).orElseThrow(() -> new CustomException(UserErrorCode.NOT_AVAILABLE_EMAIL));
        Post post = toPost(postdto);
        post.setUser(user);
        postRepository.post(post);
        return toDto(post);
    }

    @Transactional
    public void update(Long postId, Long userId, PostDto postDto) {
        Post findPost = postRepository.findById(postId).orElseThrow(() -> new CustomException(PostErrorCode.NOT_EXIST_POST));
        User findUser = userRepository.findById(userId).orElseThrow(() -> new CustomException(UserErrorCode.NOT_AVAILABLE_EMAIL));
        if (findPost.getUser() != findUser)
            throw new CustomException(PostErrorCode.NOT_EQUAL_USER);
        findPost.setTitle(postDto.getTitle());
        findPost.setContent(postDto.getContent());
        if (findPost.getPostType() == PostType.TRADE) {
            findPost.setPrice((postDto.getPrice()));
            findPost.setTradeStatus(postDto.getTradeStatus());
        }
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

    public List<PostWithIdAndPrice> findPostList(int limit, int offset, PostType postType) {
        int pageIndex = offset / Math.max(limit, 1);
        String idsKey = "posts:ids:" + postType + ":page:" + pageIndex + ":size:" + limit;

        List<String> idStrs = redisUtils.listSet(idsKey, 0, -1);

        if (idStrs != null && !idStrs.isEmpty()) {

            List<String> postKeys = idStrs.stream().map(id -> "post:" + id).toList();
            List<Object> cached = redisUtils.multiGet(postKeys);

            // cache hit
            if (cached != null && cached.stream().allMatch(Objects::nonNull)) {
                return cached.stream()
                        .map(obj -> objectMapper.convertValue(obj, PostWithIdAndPrice.class))
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
                List<Post> missingPosts = postRepository.findAllById(longIds);
                if (longIds.size() > missingPosts.size()) {
                    redisUtils.expire(idsKey, Duration.ofSeconds(10));
                    return rebuildFindWithLock(limit, offset, idsKey, postType).stream()
                            .map(obj -> objectMapper.convertValue(obj, PostWithIdAndPrice.class)).toList();
                }
                Map<Long, Post> idMap = missingPosts.stream().collect(Collectors.toMap(Post::getId, Function.identity()));

                List<PostWithIdAndPrice> result = new ArrayList<>();

                for (String id : idStrs) {
                    Object cachedObj = redisUtils.get("post:" + id);

                    if (cachedObj != null) {
                        result.add(objectMapper.convertValue(cachedObj, PostWithIdAndPrice.class));
                    } else {
                        Post p = idMap.get(Long.valueOf(id));

                        if (p != null) {
                            PostWithIdAndPrice pw = toWithIdAndPriceOnlyPost(p);
                            redisUtils.set("post:" + id, pw, POST_TTL);
                            result.add(pw);
                        } else {
                            return rebuildFindWithLock(limit, offset, idsKey, postType).stream()
                                    .map(obj -> objectMapper.convertValue(obj, PostWithIdAndPrice.class)).toList();
                        }
                    }
                }

                return result;
            }
            // 다수 누락 재빌드
            return rebuildFindWithLock(limit, offset, idsKey, postType).stream()
                    .map(obj -> objectMapper.convertValue(obj, PostWithIdAndPrice.class)).toList();
        }

        // idsKey null 재빌드
        return rebuildFindWithLock(limit, offset, idsKey, postType).stream()
                .map(obj -> objectMapper.convertValue(obj, PostWithIdAndPrice.class)).toList();
    }

    @DistributedLock(key = "'posts:ids:' + #type + ':page:' + #offset / #limit + ':size:' + #limit")
    //@Cacheable(value = "post", key = "'posts:ids:' + #type + ':page:' + #offset / #limit + ':size:' + #limit", sync = true)
    public List<?> rebuildFindWithLock(int limit, int offset, String idsKey, PostType type) {

        // double-check
        List<String> existing = redisUtils.listSet(idsKey, 0, -1);
        if (existing != null && !existing.isEmpty()) {
            List<Object> cached = redisUtils.multiGet(
                    existing.stream().map(id -> "post:" + id).collect(Collectors.toList()));
            if (cached != null && cached.stream().allMatch(Objects::nonNull)) {
                if (type.equals(PostType.FREE)) {
                    return cached.stream().map(obj -> objectMapper.convertValue(obj, PostWithId.class)).collect(Collectors.toList());
                } else if (type.equals(PostType.TRADE)) {
                    return cached.stream().map(obj -> objectMapper.convertValue(obj, PostWithIdAndPrice.class)).collect(Collectors.toList());
                }
            }
        }

        redisUtils.delete("posts:" + type + ":count");
        redisUtils.stringSet("posts:" + type + ":count", String.valueOf(postRepository.countPost(type)), COUNT_TTL);

        List<Post> posts;
        List<PostWithId> dtoListWithId;
        List<PostWithIdAndPrice> dtoListWithIdAndPrice;

        // DB 조회
        if (PostType.FREE.equals(type)) {
            posts = postRepository.findByPaging(limit, offset, PostType.FREE);
            dtoListWithId = posts.stream().map(this::toWithIdOnlyPost).toList();
            // per-post 캐시 세팅
            for (PostWithId dto : dtoListWithId) {
                redisUtils.set("post:" + dto.getId(), dto, POST_TTL);
            }

            // ids 리스트 저장
            redisUtils.delete(idsKey);
            if (!dtoListWithId.isEmpty()) {
                List<String> ids = dtoListWithId.stream().map(d -> String.valueOf(d.getId())).collect(Collectors.toList());
                redisUtils.listRightPush(idsKey, ids);
                redisUtils.expire(idsKey, IDS_TTL.plusSeconds(ThreadLocalRandom.current().nextLong(-10, 11)));
            } else {
                // 빈 결과도 짧게 캐시
                redisUtils.set(idsKey + ":empty", "1", Duration.ofSeconds(30));
            }
            return dtoListWithId;
        } else if (PostType.TRADE.equals(type)) {
            posts = postRepository.findByPaging(limit, offset, PostType.TRADE);
            dtoListWithIdAndPrice = posts.stream().map(this::toWithIdAndPriceOnlyPost).toList();

            // per-post 캐시 세팅
            for (PostWithIdAndPrice dto : dtoListWithIdAndPrice) {
                redisUtils.set("post:" + dto.getId(), dto, POST_TTL);
            }

            // ids 리스트 저장
            redisUtils.delete(idsKey);
            if (!dtoListWithIdAndPrice.isEmpty()) {
                List<String> ids = dtoListWithIdAndPrice.stream().map(d -> String.valueOf(d.getId())).collect(Collectors.toList());
                redisUtils.listRightPush(idsKey, ids);
                redisUtils.expire(idsKey, IDS_TTL.plusSeconds(ThreadLocalRandom.current().nextLong(-10, 11)));
            } else {
                // 빈 결과도 짧게 캐시
                redisUtils.set(idsKey + ":empty", "1", Duration.ofSeconds(30));
            }
            return dtoListWithIdAndPrice;
        }

        throw new CustomException(PostErrorCode.POST_TYPE_ERROR);
    }

    public Post findById(Long id) {
        return postRepository.findById(id).orElseThrow(() -> new CustomException(PostErrorCode.NOT_EXIST_POST));
    }

    public int countFreePost() {
        return postRepository.countFreePost();
    }

    public int countTradePost() {
        return postRepository.countTradePost();
    }

    public int countPost(PostType postType) {

        //return postRepository.countTradePost();

        Object objCount = redisUtils.stringGet("posts:" + postType + ":count");
        if (objCount == null) {
            int count = postRepository.countPost(postType);
            redisUtils.stringSet("posts:" + postType + ":count", String.valueOf(count), COUNT_TTL);
            return count;
        } else {
            return Integer.parseInt(objCount.toString());
        }
    }

    public SearchFreeListForm searchFreePost(String keyword, int limit, int offset) {
        List<Post> postList = postRepository.findByTitle(keyword, limit, offset, PostType.FREE);
        List<PostWithId> postDtoList = new ArrayList<>();
        if (postList.isEmpty()) {
            return SearchFreeListForm.builder()
                    .activated(false)
                    .postList(postDtoList)
                    .build();
        } else {
            for (Post post : postList) {
                postDtoList.add(PostWithId.builder()
                        .title(post.getTitle())
                        .content(post.getContent())
                        .nickname(post.getUser().getNickname())
                        .files(post.getPhoto())
                        .id(post.getId())
                        .build());
            }
            return SearchFreeListForm.builder()
                    .postList(postDtoList)
                    .activated(true)
                    .build();
        }
    }

    public SearchTradeListForm searchTradePost(String keyword, int limit, int offset) {
        List<Post> postList = postRepository.findByTitle(keyword, limit, offset, PostType.TRADE);
        List<PostWithIdAndPrice> postDtoList = new ArrayList<>();
        if (postList.isEmpty()) {
            return SearchTradeListForm.builder()
                    .activated(false)
                    .postList(postDtoList)
                    .build();
        } else {
            for (Post post : postList) {
                postDtoList.add(PostWithIdAndPrice.builder()
                        .title(post.getTitle())
                        .content(post.getContent())
                        .files(post.getPhoto())
                        .price(post.getPrice())
                        .nickname(post.getUser().getNickname())
                        .tradeStatus(post.getTradeStatus())
                        .id(post.getId())
                        .build());
            }
            return SearchTradeListForm.builder()
                    .postList(postDtoList)
                    .activated(true)
                    .build();
        }
    }

    private Post toPost(PostDto postDto) {
        Post post = new Post();
        post.setTitle(postDto.getTitle());
        post.setContent(postDto.getContent());
        post.setPostType(postDto.getPostType());
        if (postDto.getPostType() == PostType.TRADE && postDto.getPrice() != null) {
            post.setPrice(postDto.getPrice());
            post.setTradeStatus(TradeStatus.READY);
        }

        return post;
    }

    private PostDto toDto(Post post) {
        return PostDto.builder()
                .title(post.getTitle())
                .content(post.getContent())
                .tradeStatus(post.getTradeStatus())
                .postId(post.getId())
                .photo(post.getPhoto().stream().map(obj -> objectMapper.convertValue(obj, PhotoDto.class)).toList())
                .postType(post.getPostType())
                .price(post.getPrice())
                .build();
    }

    private PostWithId toWithIdOnlyPost(Post p) {
        return PostWithId.builder()
                .title(p.getTitle())
                .content(p.getContent())
                .id(p.getId())
                .title(p.getTitle())
                .nickname(p.getUser().getNickname())
                .build();
    }

    private PostWithIdAndPrice toWithIdAndPriceOnlyPost(Post p) {
        return PostWithIdAndPrice.builder()
                .title(p.getTitle())
                .content(p.getContent())
                .nickname(p.getUser().getNickname())
                .price(p.getPrice())
                .tradeStatus(p.getTradeStatus())
                .id(p.getId())
                .build();
    }
}
