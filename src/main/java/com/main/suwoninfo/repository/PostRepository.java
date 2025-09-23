package com.main.suwoninfo.repository;

import com.main.suwoninfo.domain.Post;
import com.main.suwoninfo.domain.PostType;
import com.main.suwoninfo.domain.QPost;
import com.main.suwoninfo.exception.CustomException;
import com.main.suwoninfo.exception.PostErrorCode;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

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
                        .and(post.title.like("%" + keyword + "%")
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
        return Math.toIntExact(queryFactory.select(post.count())
                .from(post)
                .where(post.postType.eq(PostType.FREE))
                .fetchOne());
    }

    public int countTradePost() {
        return Math.toIntExact(queryFactory.select(post.count())
                .from(post)
                .where(post.postType.eq(PostType.TRADE))
                .fetchOne());
    }

    public List<Post> findAllById(List<Long> longIds) {
        List<Post> rows = queryFactory.selectFrom(post)
                .where(post.id.in(longIds))
                .fetch();
        Map<Long, Post> postMap = rows.stream()
                .collect(Collectors.toMap(Post::getId, Function.identity()));

        List<Post> result = new ArrayList<>();
        for (Long id : longIds) {
            Post p = postMap.get(id);
            if (p != null) result.add(p);
        }
        return result;
    }
}

