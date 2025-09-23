package com.main.suwoninfo.repository;

import com.main.suwoninfo.domain.Comment;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.main.suwoninfo.domain.QComment.comment;

@Repository
@RequiredArgsConstructor
public class CommentRepository {

    private final EntityManager entityManager;
    private final JPAQueryFactory queryFactory;

    public void save(Comment comment) {
        entityManager.persist(comment);
    }

    public Optional<Comment> findById(Long id) {
        return entityManager.createQuery("select c from Comment c where c.id = :id", Comment.class)
                .setParameter("id", id)
                .getResultList().stream().findAny();
    }

    public List<Comment> findAll() {
        return queryFactory.selectFrom(comment)
                .fetch();
    }

    public List<Comment> findByPaging(Long id) {
        return queryFactory.selectFrom(comment)
                .leftJoin(comment.parent)
                .fetchJoin()
                .where(comment.post.id.eq(id))
                .orderBy(comment.createdTime.asc(), comment.parent.id.asc().nullsFirst())
                .fetch();
    }


    public void delete(Comment comment) {
        comment.setActivated(false);
    }

    public int countComment(Long postId) {
        return entityManager.createQuery("select c from Comment c where c.post.id = :postId", Comment.class)
                .setParameter("postId", postId)
                .getResultList().size();
    }
}