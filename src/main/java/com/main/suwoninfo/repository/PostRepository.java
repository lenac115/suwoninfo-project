package com.main.suwoninfo.repository;

import com.main.suwoninfo.domain.Post;
import com.main.suwoninfo.domain.PostType;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import java.util.List;
import java.util.Optional;

import static com.main.suwoninfo.domain.QPost.post;

@Repository
@RequiredArgsConstructor
public class PostRepository {

    private final EntityManager entityManager;
    private final JPAQueryFactory queryFactory;

    public void post(Post post) {
        entityManager.persist(post);
    }

    public Optional<Post> findById(Long postId) {
        return entityManager.createQuery("select p from Post p where p.id = :id", Post.class)
                .setParameter("id", postId)
                .getResultList().stream().findAny();
    }

    public List<Post> findByTitle(String keyword, int limit, int offset, PostType postType) {
        return queryFactory.selectFrom(post)
                .where(post.postType.eq(postType)
                        .and(post.title.like("%"+ keyword + "%")
                                .or(post.content.like("%" + keyword + "%"))))
                .offset(offset)
                .limit(limit)
                .orderBy(post.id.desc())
                .fetch();
    }

    public List<Post> findByPaging(int limit, int offset, PostType postType) {
        return queryFactory.selectFrom(post)
                .where(post.postType.eq(postType))
                .offset(offset)
                .limit(limit)
                .orderBy(post.id.desc())
                .fetch();
    }

    public void delete(Post post) {
        entityManager.remove(post);
    }

    public int countFreePost() {
        return entityManager.createQuery("select p from Post p where p.postType = 0", Post.class)
                .getResultList().size();
    }

    public int countTradePost() {
        return entityManager.createQuery("select p from Post p where p.postType = 1", Post.class)
                .getResultList().size();
    }
}

