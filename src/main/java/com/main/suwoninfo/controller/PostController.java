package com.main.suwoninfo.controller;

import com.google.gson.Gson;
import com.main.suwoninfo.domain.Photo;
import com.main.suwoninfo.domain.Post;
import com.main.suwoninfo.domain.User;
import com.main.suwoninfo.dto.PhotoDto;
import com.main.suwoninfo.dto.PostDto;
import com.main.suwoninfo.form.*;
import com.main.suwoninfo.service.PhotoService;
import com.main.suwoninfo.service.PostService;
import com.main.suwoninfo.service.UserService;
import com.main.suwoninfo.utils.CommonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 게시글 관련 컨트롤러
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/post")
public class PostController {

    private final PostService postService;
    private final UserService userService;
    private final PhotoService photoService;
    private final Gson gson;

    private static final int PAGE_SIZE = 10;


    // 게시글 작성
    @PostMapping("/free/new")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> posting(@RequestPart PostDto postForm,
                                          @AuthenticationPrincipal UserDetails user,
                                          @RequestPart(value = "files", required = false) List<MultipartFile> files)
            throws Exception {

        //postForm이 빈 경우
        if(CommonUtils.isEmpty(postForm)) {
            String message = "빈 객체 반환";
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(message);
        }

        // 포스팅
        PostDto posting = postService.post(userService.findByEmail(user.getUsername()).getId(), postForm);
        if(files != null)
            photoService.addPhoto(Photo.builder()
                .build(), files, posting.getPostId());

        return ResponseEntity.status(HttpStatus.OK).body("게시 성공");
    }

    @PostMapping("/trade/new")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> trading(@RequestPart PostDto postForm,
                                          @AuthenticationPrincipal UserDetails user,
                                          @RequestPart(value = "files", required = false) List<MultipartFile> files)
            throws Exception {

        //postForm이 빈 경우
        if(CommonUtils.isEmpty(postForm)) {
            String message = "빈 객체 반환";
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(message);
        }

        // 포스팅
        PostDto postDto = postService.post(userService.findByEmail(user.getUsername()).getId(), postForm);
        if(files != null)
            photoService.addPhoto(Photo.builder()
                .build(), files, postDto.getPostId());

        return ResponseEntity.status(HttpStatus.OK).body("게시 성공");
    }


    // 게시글 수정
    @PostMapping("/update/{postId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> update(@PathVariable Long postId,
                                         @RequestPart PostDto updateForm,
                                         @RequestPart(value = "files", required = false) List<MultipartFile> files,
                                         @AuthenticationPrincipal UserDetails user) throws Exception {

        // 빈 객체 반환
        if(CommonUtils.isEmpty(updateForm)){
            String message = "빈 객체 반환";
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(message);
        }

        if(files != null)
            photoService.addPhoto(Photo.builder()
                .build(), files, postId);

        postService.update(postId, userService.findByEmail(user.getUsername()).getId(), updateForm);

        return ResponseEntity.status(HttpStatus.OK).body("업데이트 성공");
    }

    @GetMapping("/trade/list")
    public ResponseEntity<?> tradeList(@RequestParam(defaultValue = "1") Integer page) {

        if(page == null){
            String message = "빈 객체 반환";
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(message);
        }

        int pageIndex = page - 1;
        int offset = pageIndex * PAGE_SIZE;

        List<PostWithIdAndPrice> postList = postService.findTradeByPaging(10, offset);
        int totalCount = postService.countTradePost();
        int totalPage = (totalCount + PAGE_SIZE - 1) / PAGE_SIZE;

        if (totalPage == 0 || pageIndex >= totalPage) {
            PostTradeListForm empty = PostTradeListForm.builder().postList(Collections.emptyList()).totalPage(totalPage).build();
            return ResponseEntity.ok(empty);
        }

        PostTradeListForm postListForm = PostTradeListForm.builder()
                .postList(postList)
                .totalPage(totalPage)
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(postListForm);
    }

    @GetMapping("/free/list")
    public ResponseEntity<String> freeList(@RequestParam String page) {

        if(CommonUtils.isEmpty(page)){
            String message = "빈 객체 반환";
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(message);
        }

        int pageNum = (gson.fromJson(page, int.class) - 1) * 10;

        List<PostWithId> postList = postService.findFreeByPaging(10, pageNum);
        int totalPage = postService.countFreePost();
        int countPage = totalPage / 10;
        if (totalPage % 10 > 0)
            countPage += 1;
        PostListForm postListForm = PostListForm.builder()
                .postList(postList)
                .totalPage(countPage)
                .build();
        String listJson = gson.toJson(postListForm);
        return ResponseEntity.status(HttpStatus.OK).body(listJson);
    }

    @GetMapping("/{postId}")
    public ResponseEntity<String> view(@PathVariable Long postId) {

        if(CommonUtils.isEmpty(postId)){
            String message = "빈 객체 반환";
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(message);
        }
        Post postDetail = postService.findById(postId);
        if(postDetail.getPhoto().isEmpty()) {
            PostWithNickName postDto = PostWithNickName.builder()
                    .content(postDetail.getContent())
                    .title(postDetail.getTitle())
                    .price(Integer.toString(postDetail.getPrice()))
                    .tradeStatus(postDetail.getTradeStatus())
                    .postType(postDetail.getPostType())
                    .nickname(postDetail.getUser().getNickname())
                    .build();
            String detailJson = gson.toJson(postDto);

            return ResponseEntity.status(HttpStatus.OK).body(detailJson);
        }
        int size = postDetail.getPhoto().size();
        List<PhotoDto> photoList = new ArrayList<>();
        for(int i = 0; i < size; i++) {
            PhotoDto photo = PhotoDto.builder()
                    .photoId(postDetail.getPhoto().get(i).getId())
                    .filePath(postDetail.getPhoto().get(i).getFilePath())
                    .fileSize(postDetail.getPhoto().get(i).getFileSize())
                    .origFileName(postDetail.getPhoto().get(i).getOrigFileName())
                    .build();
            photoList.add(photo);
        }
        PostWithNickName postDto = PostWithNickName.builder()
                .content(postDetail.getContent())
                .title(postDetail.getTitle())
                .postType(postDetail.getPostType())
                .photo(photoList)
                .price(Integer.toString(postDetail.getPrice()))
                .tradeStatus(postDetail.getTradeStatus())
                .nickname(postDetail.getUser().getNickname())
                .build();
        String detailJson = gson.toJson(postDto);

        return ResponseEntity.status(HttpStatus.OK).body(detailJson);
    }

    @DeleteMapping("/delete/{postId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<String> delete(@PathVariable Long postId, @AuthenticationPrincipal UserDetails userDetails) {
        if(CommonUtils.isEmpty(postId)){
            String message = "빈 객체 반환";
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(message);
        }
        User user = userService.findByEmail(userDetails.getUsername());
        postService.delete(postId, userDetails.getUsername());

        String message = "삭제 성공";

        return ResponseEntity.status(HttpStatus.OK).body(message);
    }

    @GetMapping("/free/search")
    public ResponseEntity<String> freeSearch(@RequestParam String keyword, @RequestParam int page) {

        int pageNum = (page - 1) * 10;
        SearchFreeListForm postList = postService.searchFreePost(keyword, 10, pageNum);
        int totalPage = postService.countFreePost();
        int countPage = totalPage / 10;
        if (totalPage % 10 > 0)
            countPage += 1;


        if(!postList.isActivated())
            return ResponseEntity.status(HttpStatus.OK).body("결과값이 존재하지 않음");

        postList.setTotalPage(countPage);

        String listJson = gson.toJson(postList);

        return ResponseEntity.status(HttpStatus.OK).body(listJson);
    }

    @GetMapping("/trade/search")
    public ResponseEntity<String> tradeSearch(@RequestParam String keyword, @RequestParam int page) {

        int pageNum = (page - 1) * 10;
        SearchTradeListForm postList = postService.searchTradePost(keyword, 10, pageNum);
        int totalPage = postService.countFreePost();
        int countPage = totalPage / 10;
        if (totalPage % 10 > 0)
            countPage += 1;


        if(!postList.isActivated())
            return ResponseEntity.status(HttpStatus.OK).body("결과값이 존재하지 않음");

        postList.setTotalPage(countPage);

        String listJson = gson.toJson(postList);

        return ResponseEntity.status(HttpStatus.OK).body(listJson);
    }
}
