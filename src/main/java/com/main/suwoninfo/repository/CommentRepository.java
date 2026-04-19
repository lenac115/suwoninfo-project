package com.main.suwoninfo.repository;

import com.main.suwoninfo.domain.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    Comment save(Comment comment);

    Optional<Comment> findById(Long id);

    List<Comment> findAll();

    @Query("select count(c) from Comment c where c.post.id = :postId")
    int countComment(Long postId);
}