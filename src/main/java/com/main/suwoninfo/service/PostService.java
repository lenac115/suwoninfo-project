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
import com.main.suwoninfo.redis.DistributedLock;
import com.main.suwoninfo.repository.PostRepository;
import com.main.suwoninfo.repository.UserRepository;
import com.main.suwoninfo.utils.RedisUtils;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final RedisUtils redisUtils;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    private Timer redisLrangeTrade;
    private Timer redisMgetTrade;
    private Timer apiTradeRebuild;
    private Counter cachePostHit;
    private Counter cachePostRebuild;

    @PostConstruct
    void initService() {
        this.redisLrangeTrade = meterRegistry.timer("redis.lrange.trade");
        this.redisMgetTrade = meterRegistry.timer("redis.mget.trade");
        this.apiTradeRebuild = meterRegistry.timer("api.posts.trade.rebuild");
        this.cachePostHit = meterRegistry.counter("cache.post.hit");
        this.cachePostRebuild = meterRegistry.counter("cache.post.rebuild");
    }

    @Transactional
    public PostDto post(Long userId, PostDto postdto) {

        User user = userRepository.findById(userId).orElseThrow(() -> new CustomException(UserErrorCode.NOT_AVAILABLE_EMAIL));
        Post post = toPost(postdto);
        post.setUser(user);
        postRepository.post(post);
        return toDto(post);
    }

    @Transactional
    public void update(Long postId, Long userId, PostDto postDto) {
        Post findPost = postRepository.findById(postId).orElseThrow(() -> new CustomException(PostErrorCode.NOT_EXIST_POST));
        User findUser = userRepository.findById(userId).orElseThrow(() -> new CustomException(UserErrorCode.NOT_AVAILABLE_EMAIL));;
        if (findPost.getUser() != findUser)
            throw new CustomException(PostErrorCode.NOT_EQUAL_USER);
        findPost.setTitle(postDto.getTitle());
        findPost.setContent(postDto.getContent());
        if(findPost.getPostType() == PostType.TRADE){
            findPost.setPrice((postDto.getPrice()));
            findPost.setTradeStatus(postDto.getTradeStatus());
        }
    }

    @Transactional
    public void delete(Long postId, String email) {
        Post post = findById(postId);
        User user = userRepository.findByEmail(email).orElseThrow(() -> new CustomException(UserErrorCode.NOT_EXIST_EMAIL));
        if(!Objects.equals(post.getUser().getId(), user.getId()))
            throw new CustomException(PostErrorCode.NOT_EQUAL_USER);
        postRepository.delete(post);
        for(int i = 0; i<post.getComment().size(); i++){
            post.getComment().get(i).setActivated(false);
        }
    }

    public List<PostWithId> findFreeByPagingOrigin(int limit, int offset) {

        List<Post> postList = postRepository.findByPaging(limit, offset, PostType.FREE);
        List<PostWithId> postDtoList = new ArrayList<>();

        postList.forEach(post -> postDtoList.add(PostWithId.builder()
                        .title(post.getTitle())
                        .content(post.getContent())
                        .nickname(post.getUser().getNickname())
                        .id(post.getId())
                .build()));

        return postDtoList;
    }

    public List<PostWithId> findFreeByPaging(int limit, int offset) {
        int pageIndex = offset / Math.max(limit, 1);
        String idsKey = "posts:ids:free:page:" + pageIndex + ":size:" + limit;

        List<String> idStrs = redisUtils.listSet(idsKey, 0, -1);

        if(idStrs != null && !idStrs.isEmpty()) {

            List<String> postKeys = idStrs.stream().map(id -> "post:" + id).toList();
            List<Object> cached = redisUtils.multiGet(postKeys);

            // cache hit
            if(cached != null && cached.stream().allMatch(Objects::nonNull)) {
                return cached.stream()
                        .map(obj -> objectMapper.convertValue(obj, PostWithId.class))
                        .collect(Collectors.toList());
            }

            // cache miss시 일부 누락이면 보충 or 재빌드
            List<String> missingIds = new ArrayList<>();
            for (int i = 0; i < cached.size(); i++) {
                if(cached.get(i) == null) missingIds.add(idStrs.get(i));
            }

            if(!missingIds.isEmpty() && missingIds.size() <= 3) {
                List<Long> longIds = missingIds.stream().map(Long::parseLong).toList();
                List<Post> missingPosts = postRepository.findAllById(longIds);
                if(longIds.size() > missingPosts.size()) {
                    redisUtils.expire(idsKey, Duration.ofSeconds(10));
                    return rebuildFindWithLock(limit, offset, idsKey, PostType.FREE).stream()
                            .map(obj -> objectMapper.convertValue(obj, PostWithId.class)).toList();
                }
                Map<Long, Post> idMap = missingPosts.stream().collect(Collectors.toMap(Post::getId, Function.identity()));

                List<PostWithId> result = new ArrayList<>();

                for(String id : idStrs) {
                    Object cachedObj = redisUtils.get("post:" +id);

                    if(cachedObj != null) {
                        result.add(objectMapper.convertValue(cachedObj, PostWithId.class));
                    } else {
                        Post p = idMap.get(Long.valueOf(id));

                        if(p != null) {
                            PostWithId pw = toWithId(p);
                            redisUtils.set("post:" + id, pw, POST_TTL);
                            result.add(pw);
                        } else {
                            return rebuildFindWithLock(limit, offset, idsKey, PostType.FREE).stream()
                                    .map(obj -> objectMapper.convertValue(obj, PostWithId.class)).toList();
                        }
                    }
                }

                return result;
            }
            // 다수 누락 재빌드
            return rebuildFindWithLock(limit, offset, idsKey, PostType.FREE).stream()
                    .map(obj -> objectMapper.convertValue(obj, PostWithId.class)).toList();
        }

        // idsKey null 재빌드
        return rebuildFindWithLock(limit, offset, idsKey, PostType.FREE).stream()
                .map(obj -> objectMapper.convertValue(obj, PostWithId.class)).toList();
    }

    @Timed(value = "api.posts.trade.db", histogram = true)
    public List<PostWithIdAndPrice> findTradeByPagingOrigin(int limit, int offset) {
        Timer.Sample s = Timer.start(meterRegistry);
        List<Post> postList = postRepository.findByPaging(limit, offset, PostType.TRADE);
        List<PostWithIdAndPrice> postDtoList = new ArrayList<>();
        postList.forEach(post -> postDtoList.add(
                PostWithIdAndPrice.builder()
                        .title(post.getTitle())
                        .content(post.getContent())
                        .nickname(post.getUser().getNickname())
                        .id(post.getId())
                        .price(post.getPrice())
                        .tradeStatus(post.getTradeStatus())
                        .build()
        ));
        s.stop(Timer.builder("db.query.trade.list").register(meterRegistry));
        return postDtoList;
    }

    @Timed(value = "api.posts.trade", histogram = true)
    public List<PostWithIdAndPrice> findTradeByPaging(int limit, int offset) {
        int pageIndex = offset / Math.max(limit, 1);
        String idsKey = "posts:ids:trade:page:" + pageIndex + ":size:" + limit;

        List<String> idStrs = redisLrangeTrade
                .record(() -> redisUtils.listSet(idsKey, 0, -1));


        if(idStrs != null && !idStrs.isEmpty()) {
            List<String> postKeys = idStrs.stream().map(id -> "post:" + id).collect(Collectors.toList());
            List<Object> cached = redisMgetTrade.record(() -> redisUtils.multiGet(postKeys));

            if(cached != null && cached.stream().allMatch(Objects::nonNull)) {
                cachePostHit.increment();
                return (List) cached;
            }

            List<String> missingIds = new ArrayList<>();

            for (int i = 0; i < cached.size(); i++) {
                if (cached.get(i) == null) {
                    missingIds.add(idStrs.get(i));
                }
            }

            if(missingIds.size() <= 3 && !missingIds.isEmpty()) {
                List<Long> longIds = missingIds.stream().map(Long::parseLong).toList();
                List<Post> missingPost = postRepository.findAllById(longIds);

                if(longIds.size() > missingPost.size()) {
                    redisUtils.expire(idsKey, Duration.ofSeconds(10));
                    cachePostRebuild.increment();
                    return apiTradeRebuild
                            .record(() -> (List) rebuildFindWithLock(limit, offset, idsKey, PostType.TRADE));
                }

                Map<Long, Post> idMap = missingPost.stream().collect(Collectors.toMap(Post::getId, Function.identity()));
                List<PostWithIdAndPrice> result =  new ArrayList<>();

                for (String id : idStrs) {
                    Object cachedObj = redisUtils.get("post:" +id);

                    if(cachedObj != null) {
                        result.add(objectMapper.convertValue(cachedObj, PostWithIdAndPrice.class));
                    } else {
                        Post p = idMap.get(Long.valueOf(id));
                        if(p != null) {
                            PostWithIdAndPrice post = toWithIdAndPrice(p);
                            redisUtils.set("post:" + id, post, POST_TTL);
                            result.add(post);
                        } else {
                            cachePostRebuild.increment();
                            return apiTradeRebuild
                                    .record(() -> (List) rebuildFindWithLock(limit, offset, idsKey, PostType.TRADE));
                        }
                    }
                }
                cachePostHit.increment();
                return result;
            }
            cachePostRebuild.increment();
            return apiTradeRebuild
                    .record(() -> (List) rebuildFindWithLock(limit, offset, idsKey, PostType.TRADE));
        }
        cachePostRebuild.increment();
        return apiTradeRebuild
                .record(() -> (List) rebuildFindWithLock(limit, offset, idsKey, PostType.TRADE));
    }

    @DistributedLock(key = "'posts:ids:' + #type + ':page:' + #offset / #limit + ':size:' + #limit")
    public List<?> rebuildFindWithLock(int limit, int offset, String idsKey, PostType type) {

        // double-check
        List<String> existing = redisUtils.listSet(idsKey, 0, -1);
        if (existing != null && !existing.isEmpty()) {
            List<Object> cached = redisUtils.multiGet(
                    existing.stream().map(id -> "post:" + id).collect(Collectors.toList()));
            if (cached != null && cached.stream().allMatch(Objects::nonNull)) {
                if(type.equals(PostType.FREE)) {
                    return cached.stream().map(obj -> objectMapper.convertValue(obj, PostWithId.class)).collect(Collectors.toList());
                }
                else if (type.equals(PostType.TRADE)) {
                    return cached.stream().map(obj -> objectMapper.convertValue(obj, PostWithIdAndPrice.class)).collect(Collectors.toList());
                }
            }
        }

        List<Post> posts;
        List<PostWithId> dtoListWithId;
        List<PostWithIdAndPrice> dtoListWithIdAndPrice;

        // DB 조회
        if(PostType.FREE.equals(type)) {
            posts = postRepository.findByPaging(limit, offset, PostType.FREE);
            dtoListWithId = posts.stream().map(this::toWithId).toList();
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
            dtoListWithIdAndPrice = posts.stream().map(this::toWithIdAndPrice).toList();

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

    public SearchFreeListForm searchFreePost(String keyword, int limit, int offset) {
        List<Post> postList = postRepository.findByTitle(keyword, limit, offset, PostType.FREE);
        List<PostWithId> postDtoList = new ArrayList<>();
        if(postList.isEmpty()) {
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
        if(postList.isEmpty()) {
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

    private String sha1Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private Post toPost(PostDto postDto) {
        Post post = new Post();
        post.setTitle(postDto.getTitle());
        post.setContent(postDto.getContent());
        post.setPostType(postDto.getPostType());
        if(postDto.getPostType() == PostType.TRADE && postDto.getPrice() != null){
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

    private PostWithId toWithId(Post p) {
        return PostWithId.builder()
                .content(p.getContent())
                .id(p.getId())
                .title(p.getTitle())
                .files(p.getPhoto())
                .nickname(p.getUser().getNickname())
                .build();
    }

    private PostWithIdAndPrice toWithIdAndPrice(Post p) {
        return PostWithIdAndPrice.builder()
                .content(p.getContent())
                .nickname(p.getUser().getNickname())
                .price(p.getPrice())
                .tradeStatus(p.getTradeStatus())
                .id(p.getId())
                .files(p.getPhoto())
                .build();
    }
}
