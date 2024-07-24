package com.main.suwoninfo.service;

import com.main.suwoninfo.domain.Post;
import com.main.suwoninfo.domain.PostType;
import com.main.suwoninfo.domain.TradeStatus;
import com.main.suwoninfo.domain.User;
import com.main.suwoninfo.dto.PostDto;
import com.main.suwoninfo.form.PostWithId;
import com.main.suwoninfo.exception.CustomException;
import com.main.suwoninfo.exception.PhotoErrorCode;
import com.main.suwoninfo.exception.PostErrorCode;
import com.main.suwoninfo.exception.UserErrorCode;
import com.main.suwoninfo.form.PostWithIdAndPrice;
import com.main.suwoninfo.form.SearchFreeListForm;
import com.main.suwoninfo.form.SearchTradeListForm;
import com.main.suwoninfo.repository.PhotoRepository;
import com.main.suwoninfo.repository.PostRepository;
import com.main.suwoninfo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PhotoRepository photoRepository;
    private final FileHandler fileHandler;

    @Transactional
    public Post post(Long userId, PostDto postdto) {

        User user = userRepository.findById(userId).orElseThrow(() -> new CustomException(UserErrorCode.NOT_AVAILABLE_EMAIL));
        Post post = dtoToPost(postdto);
        post.setUser(user);
        postRepository.post(post);
        return post;
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
            findPost.setPrice(Integer.parseInt(postDto.getPrice()));
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

    public Post dtoToPost(PostDto postDto) {
        Post post = new Post();
        post.setTitle(postDto.getTitle());
        post.setContent(postDto.getContent());
        post.setPostType(postDto.getPostType());
        if(postDto.getPostType() == PostType.TRADE && postDto.getPrice() != null){
            post.setPrice(Integer.parseInt(postDto.getPrice()));
            post.setTradeStatus(TradeStatus.READY);
        }

        //List<Photo> photoList = fileHandler.parseFileInfo(postDto.getFiles());

        /*if(!photoList.isEmpty()) {
            for (Photo photo : photoList)
                post.getPhoto().add(photoRepository.save(photo));
        }*/

        return post;
    }

    public List<PostWithId> findFreeByPaging(int limit, int offset) {
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

    public List<PostWithIdAndPrice> findTradeByPaging(int limit, int offset) {
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
        return postDtoList;
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
}
