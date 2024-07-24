package com.main.suwoninfo.service;

import com.main.suwoninfo.domain.Comment;
import com.main.suwoninfo.domain.Post;
import com.main.suwoninfo.domain.User;
import com.main.suwoninfo.dto.*;
import com.main.suwoninfo.exception.CommentErrorCode;
import com.main.suwoninfo.exception.CustomException;
import com.main.suwoninfo.exception.PostErrorCode;
import com.main.suwoninfo.exception.UserErrorCode;
import com.main.suwoninfo.form.CommentWithParent;
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

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    @Transactional
    public CommentDto notReplyPost(String email, Long boardId, String detail) {
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
        return CommentDto.builder()
                .id(comment.getId())
                .content(comment.getContent())
                .build();
    }

    @Transactional
    public CommentDto replyPost(String email, Long boardId, Long commentId, String detail) {
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
        return CommentDto.builder()
                .id(comment.getId())
                .content(comment.getContent())
                .build();
    }

    public List<CommentWithParent> findByPaging(Long postId) {
        List<Comment> comment = commentRepository.findByPaging(postId);
        List<CommentWithParent> result = new ArrayList<>();
        Map<Long, CommentWithParent> map = new HashMap<>();

        comment.forEach(c -> {
            CommentWithParent commentWithParent;
            if(c.isActivated()) {
                commentWithParent = CommentWithParent.builder()
                        .content(c.getContent())
                        .id(c.getId())
                        .author(c.getUser().getNickname())
                        .children(new ArrayList<>())
                        .build();
            } else {
                commentWithParent = CommentWithParent.builder()
                        .content("삭제된 댓글입니다.")
                        .id(c.getId())
                        .author(c.getUser().getNickname())
                        .children(new ArrayList<>())
                        .build();
            }
                    if(c.getParent() != null){
                        commentWithParent.setParent(c.getParent().getId());
                    }
                    map.put(commentWithParent.getId(), commentWithParent);
                    if (c.getParent() != null)
                        map.get(c.getParent().getId()).getChildren().add(commentWithParent);
                    else result.add(commentWithParent);
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
                .orElseThrow(() ->  new CustomException(CommentErrorCode.NOT_EXIST_COMMENT));
        User findUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(UserErrorCode.NOT_EXIST_EMAIL));

        if(comment.getUser().getId() != findUser.getId())
            throw new CustomException(UserErrorCode.NOT_EQUAL_EMAIL);
        comment.setContent(detail);
    }

    @Transactional
    public void delete(Long commentId, String email) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() ->  new CustomException(CommentErrorCode.NOT_EXIST_COMMENT));
        User findUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(UserErrorCode.NOT_EXIST_EMAIL));

        if(comment.getUser().getId() != findUser.getId())
            throw new CustomException(UserErrorCode.NOT_EQUAL_EMAIL);

        commentRepository.delete(comment);
    }

    public int countComment(Long postId) {
        return commentRepository.countComment(postId);
    }
}