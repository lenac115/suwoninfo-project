package com.main.suwoninfo.service;

import com.main.suwoninfo.domain.Comment;
import com.main.suwoninfo.domain.Post;
import com.main.suwoninfo.domain.User;
import com.main.suwoninfo.dto.*;
import com.main.suwoninfo.exception.CommentErrorCode;
import com.main.suwoninfo.exception.CustomException;
import com.main.suwoninfo.exception.PostErrorCode;
import com.main.suwoninfo.exception.UserErrorCode;
import com.main.suwoninfo.repository.CommentRepository;
import com.main.suwoninfo.repository.PostRepository;
import com.main.suwoninfo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.main.suwoninfo.utils.ToUtils.toCommentResponse;
import static com.main.suwoninfo.utils.ToUtils.toUserResponse;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    @Transactional
    public CommentResponse notReplyPost(String email, Long boardId, String detail) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(UserErrorCode.NOT_EXIST_EMAIL));
        Post post = postRepository.findById(boardId)
                .orElseThrow(() -> new CustomException(PostErrorCode.NOT_EXIST_POST));

        Comment comment = new Comment();
        comment.setUser(user);
        comment.setPost(post);
        comment.setActivated(true);
        comment.setContent(detail);

        commentRepository.save(comment);
        return CommentResponse.builder()
                .id(comment.getId())
                .content(comment.getContent())
                .build();
    }

    @Transactional
    public CommentResponse replyPost(String email, Long boardId, Long commentId, String detail) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(UserErrorCode.NOT_EXIST_EMAIL));
        Post post = postRepository.findById(boardId)
                .orElseThrow(() -> new CustomException(PostErrorCode.NOT_EXIST_POST));
        Comment parent = commentRepository.findById(commentId)
                .orElseThrow(() -> new CustomException(CommentErrorCode.NOT_EXIST_COMMENT));

        Comment comment = new Comment();
        comment.setUser(user);
        comment.setPost(post);
        comment.setContent(detail);
        comment.setActivated(true);
        comment.addReplyComment(parent);

        commentRepository.save(comment);
        return CommentResponse.builder()
                .id(comment.getId())
                .content(comment.getContent())
                .build();
    }

    public List<CommentResponse> findByPaging(Long postId) {
        List<Comment> comment = commentRepository.findByPaging(postId);
        List<CommentResponse> result = new ArrayList<>();
        Map<Long, CommentResponse> map = new HashMap<>();

        comment.forEach(c -> {
                    CommentResponse commentResponse;
                    if (c.isActivated()) {
                        commentResponse = CommentResponse.builder()
                                .content(c.getContent())
                                .id(c.getId())
                                .user(toUserResponse(c.getUser()))
                                .children(new ArrayList<>())
                                .parent(toCommentResponse(c.getParent()))
                                .build();
                    } else {
                        commentResponse = CommentResponse.builder()
                                .content("삭제된 댓글입니다.")
                                .id(c.getId())
                                .user(toUserResponse(c.getUser()))
                                .parent(toCommentResponse(c.getParent()))
                                .children(new ArrayList<>())
                                .build();
                    }
                    /*if (c.getParent() != null) {
                        commentResponse.parent(toCommentResponse(c.getParent()));
                    }*/
                    map.put(commentResponse.id(), commentResponse);
                    if (c.getParent() != null)
                        map.get(c.getParent().getId()).children().add(commentResponse);
                    else result.add(commentResponse);
                }
        );
        return result;
    }


    public Comment findById(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new CustomException(CommentErrorCode.NOT_EXIST_COMMENT));
    }

    @Transactional
    public void update(Long commentId, String email, String detail) {

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CustomException(CommentErrorCode.NOT_EXIST_COMMENT));
        User findUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(UserErrorCode.NOT_EXIST_EMAIL));

        if (comment.getUser().getId() != findUser.getId())
            throw new CustomException(UserErrorCode.NOT_EQUAL_EMAIL);
        comment.setContent(detail);
    }

    @Transactional
    public void delete(Long commentId, String email) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CustomException(CommentErrorCode.NOT_EXIST_COMMENT));
        User findUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(UserErrorCode.NOT_EXIST_EMAIL));

        if (comment.getUser().getId() != findUser.getId())
            throw new CustomException(UserErrorCode.NOT_EQUAL_EMAIL);

        commentRepository.delete(comment);
    }

    public int countComment(Long postId) {
        return commentRepository.countComment(postId);
    }
}