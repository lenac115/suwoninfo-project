package com.main.suwoninfo.controller;

import com.main.suwoninfo.domain.Photo;
import com.main.suwoninfo.domain.Post;
import com.main.suwoninfo.domain.User;
import com.main.suwoninfo.dto.PostRequest;
import com.main.suwoninfo.dto.PostResponse;
import com.main.suwoninfo.service.PhotoService;
import com.main.suwoninfo.service.PostFacade;
import com.main.suwoninfo.service.PostService;
import com.main.suwoninfo.service.UserService;
import com.main.suwoninfo.utils.CommonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StopWatch;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static com.main.suwoninfo.utils.ToUtils.toPostResponse;

/**
 * 게시글 관련 컨트롤러
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/post")
@Slf4j
public class PostController {

    private final PostService postService;
    private final PostFacade postFacade;
    private final UserService userService;
    private final PhotoService photoService;

    private static final int PAGE_SIZE = 10;


    // 게시글 작성
    @PostMapping("/new")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> posting(@RequestPart PostRequest postForm,
                                     @AuthenticationPrincipal UserDetails user,
                                     @RequestPart(value = "files", required = false) List<MultipartFile> files)
            throws Exception {

        //postForm이 빈 경우
        if (CommonUtils.isEmpty(postForm)) {
            String message = "빈 객체 반환";
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(message);
        }

        // 포스팅
        PostResponse posting = postService.post(userService.findByEmail(user.getUsername()).getId(), postForm);
        if (files != null)
            photoService.addPhoto(Photo.builder()
                    .build(), files, posting.postId());

        return ResponseEntity.status(HttpStatus.OK).body("게시 성공");
    }

    // 게시글 수정
    @PostMapping("/update/{postId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> update(@PathVariable Long postId,
                                    @RequestPart PostResponse updateForm,
                                    @RequestPart(value = "files", required = false) List<MultipartFile> files,
                                    @AuthenticationPrincipal UserDetails user) throws Exception {

        // 빈 객체 반환
        if (CommonUtils.isEmpty(updateForm)) {
            String message = "빈 객체 반환";
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(message);
        }

        if (files != null)
            photoService.addPhoto(Photo.builder()
                    .build(), files, postId);

        postService.update(postId, userService.findByEmail(user.getUsername()).getId(), updateForm);

        return ResponseEntity.status(HttpStatus.OK).body("업데이트 성공");
    }

    @GetMapping("/list")
    public ResponseEntity<?> joinList(@RequestParam(defaultValue = "1") Integer page, @RequestParam Post.PostType type) {

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        if (page == null) {
            String message = "빈 객체 반환";
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(message);
        }

        int pageIndex = page - 1;

        List<PostResponse> postList = postFacade.findPostList(10, page, type);
        int totalCount = postService.countPost(type);
        //int totalCount = postService.countTradePost();
        int totalPage = (totalCount + PAGE_SIZE - 1) / PAGE_SIZE;

        if (totalPage == 0 || pageIndex >= totalPage) {
            return ResponseEntity.ok(null);
        }

        stopWatch.stop();
        log.info("=============================================");
        log.info("조건: Type={}, Page={}", "TRADE", page);
        log.info("조회 건수: {}개", postList.size());
        log.info("걸린 시간: {} ms ({} 초)",
                stopWatch.getTotalTimeMillis(),
                stopWatch.getTotalTimeSeconds());
        log.info("=============================================");

        return ResponseEntity.status(HttpStatus.OK).body(postList);
    }

    @GetMapping("/view/{postId}")
    public ResponseEntity<?> view(@PathVariable Long postId) {

        if (CommonUtils.isEmpty(postId)) {
            String message = "빈 객체 반환";
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(message);
        }
        PostResponse postResponse = toPostResponse(postService.findById(postId));

        return ResponseEntity.status(HttpStatus.OK).body(postResponse);
    }

    @DeleteMapping("/delete/{postId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<String> delete(@PathVariable Long postId, @AuthenticationPrincipal UserDetails userDetails) {
        if (CommonUtils.isEmpty(postId)) {
            String message = "빈 객체 반환";
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(message);
        }
        User user = userService.findByEmail(userDetails.getUsername());
        postService.delete(postId, userDetails.getUsername());

        String message = "삭제 성공";

        return ResponseEntity.status(HttpStatus.OK).body(message);
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam String keyword, @RequestParam int page, @RequestParam Post.PostType type) {

        int pageNum = (page - 1) * 10;
        List<PostResponse> postList = postService.searchPost(keyword, 10, pageNum, type);
        int totalPage = postService.countPost(type);
        int countPage = totalPage / 10;
        if (totalPage % 10 > 0)
            countPage += 1;

        return ResponseEntity.status(HttpStatus.OK)
                .header("total-pages", String.valueOf(countPage))
                .body(postList);
    }
}
