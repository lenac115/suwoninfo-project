package com.main.suwoninfo.controller;

import com.google.gson.Gson;
import com.main.suwoninfo.dto.CommentDto;
import com.main.suwoninfo.form.CommentListForm;
import com.main.suwoninfo.form.CommentWithParent;
import com.main.suwoninfo.form.CommentWithPostId;
import com.main.suwoninfo.form.ReplyWithComment;
import com.main.suwoninfo.service.CommentService;
import com.main.suwoninfo.service.UserService;
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
    private final UserService userService;
    private final Gson gson;

    // 코멘트 뷰
    @GetMapping("/{postId}")
    public ResponseEntity<String> viewComment(@PathVariable Long postId, @RequestParam String page) {
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

        /*//Long postLongId = gson.fromJson(postId, Long.class);
        int pageNum = (Integer.parseInt(page) - 1) * 10;
        List<CommentWithParent> postDetail = commentService.findByPaging(postId, 10, pageNum);

        //코멘트에 paging 옵션을 준다.
        int totalPage = commentService.countComment(postId);
        int countPage = totalPage / 10;
        if (totalPage % 10 > 0)
            countPage += 1;

        //form으로 변경
        CommentListForm commentListForm = CommentListForm.builder()
                .commentList(postDetail)
                .totalPage(countPage)
                .build();*/

        String detailJson = gson.toJson(commentListForm);

        return ResponseEntity.status(HttpStatus.OK).body(detailJson);
    }

    //답글이 아닌 코멘트 작성
    @PostMapping("/notreply")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<String> notReplyAddComment(@RequestBody String comment,
                             @AuthenticationPrincipal UserDetails user) {

        //CommentWithPostId 폼 형식으로 작성된 Json String을 객체로 변환
        CommentWithPostId commentWithPostId = gson.fromJson(comment, CommentWithPostId.class);
        //Comment의 형식을 가진 commentDto로 저장
        CommentDto commentDto = commentService.notReplyPost(user.getUsername(), commentWithPostId.getPostId(),
                commentWithPostId.getContent());
        //Http message로 OK사인과 함께 comment의 내용 보냄
        String commentJson = gson.toJson(commentDto);

        return ResponseEntity.status(HttpStatus.OK).body(commentJson);
    }

    //코멘트의 답글 작성
    @PostMapping("/reply")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<String> replyAddComment(@RequestBody String comment,
                                  @AuthenticationPrincipal UserDetails user) {
        //ReplyWithComment 폼 형식으로 작성된 Json String을 객체로 변환
        ReplyWithComment replyWithComment = gson.fromJson(comment, ReplyWithComment.class);

        //Comment의 형식을 가진 commentDto로 저장
        CommentDto commentDto = commentService.replyPost(user.getUsername(), replyWithComment.getPostId(),
                replyWithComment.getParentId(), replyWithComment.getContent());
        //Http message로 OK사인과 함께 comment의 내용 보냄
        String commentJson = gson.toJson(commentDto);

        return ResponseEntity.status(HttpStatus.OK).body(commentJson);
    }

    // 코멘트 수정
    @PostMapping("/update/{commentId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<String> update(@PathVariable Long commentId, @RequestBody String comment,
                         @AuthenticationPrincipal UserDetails user) {

        //comment는 글만 있는 형식이기 때문에 String을 그대로 받아 사용
        commentService.update(commentId, user.getUsername(), comment);
        String success = "업데이트 성공";

        return ResponseEntity.status(HttpStatus.OK).body(success);
    }

    // 코멘트 삭제
    @DeleteMapping("/delete/{commentId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<String> delete(@PathVariable Long commentId, @AuthenticationPrincipal UserDetails user) {

        //삭제 명령
        commentService.delete(commentId, user.getUsername());
        String message = "코멘트 삭제 완료";
        return ResponseEntity.status(HttpStatus.OK).body(message);
    }
}
