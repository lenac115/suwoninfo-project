package com.main.suwoninfo.controller;

import com.main.suwoninfo.dto.CommentDto;
import com.main.suwoninfo.form.CommentListForm;
import com.main.suwoninfo.form.CommentWithParent;
import com.main.suwoninfo.form.CommentWithPostId;
import com.main.suwoninfo.form.ReplyWithComment;
import com.main.suwoninfo.service.CommentService;
import com.main.suwoninfo.utils.CommonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Comment관련 Controller
 */

@RestController
@RequiredArgsConstructor
@RequestMapping("/comment")
public class CommentController {

    private final CommentService commentService;

    // 코멘트 뷰
    @GetMapping("/{postId}")
    public ResponseEntity<?> viewComment(@PathVariable Long postId, @RequestParam String page) {
        if(CommonUtils.isEmpty(postId)){
            String message = "빈 객체 반환";
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(message);
        }
        //Long postLongId = gson.fromJson(postId, Long.class);
        List<CommentWithParent> postDetail = commentService.findByPaging(postId);

        //form으로 변경
        CommentListForm commentListForm = CommentListForm.builder()
                .commentList(postDetail)
                .build();

        return ResponseEntity.status(HttpStatus.OK).body(commentListForm);
    }

    //답글이 아닌 코멘트 작성
    @PostMapping("/notreply")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> notReplyAddComment(@RequestBody CommentWithPostId comment,
                             @AuthenticationPrincipal UserDetails user) {

        //Comment의 형식을 가진 commentDto로 저장
        CommentDto commentDto = commentService.notReplyPost(user.getUsername(), comment.getPostId(),
                comment.getContent());

        return ResponseEntity.status(HttpStatus.OK).body(commentDto);
    }

    //코멘트의 답글 작성
    @PostMapping("/reply")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> replyAddComment(@RequestBody ReplyWithComment comment,
                                  @AuthenticationPrincipal UserDetails user) {

        //Comment의 형식을 가진 commentDto로 저장
        CommentDto commentDto = commentService.replyPost(user.getUsername(), comment.getPostId(),
                comment.getParentId(), comment.getContent());

        return ResponseEntity.status(HttpStatus.OK).body(commentDto);
    }

    // 코멘트 수정
    @PostMapping("/update/{commentId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> update(@PathVariable Long commentId, @RequestBody String comment,
                         @AuthenticationPrincipal UserDetails user) {

        //comment는 글만 있는 형식이기 때문에 String을 그대로 받아 사용
        commentService.update(commentId, user.getUsername(), comment);
        String success = "업데이트 성공";

        return ResponseEntity.status(HttpStatus.OK).body(success);
    }

    // 코멘트 삭제
    @DeleteMapping("/delete/{commentId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> delete(@PathVariable Long commentId, @AuthenticationPrincipal UserDetails user) {

        //삭제 명령
        commentService.delete(commentId, user.getUsername());
        String message = "코멘트 삭제 완료";
        return ResponseEntity.status(HttpStatus.OK).body(message);
    }
}
