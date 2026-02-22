package com.main.suwoninfo.repository;

import com.main.suwoninfo.domain.Post;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.main.suwoninfo.domain.QPhoto.photo;
import static com.main.suwoninfo.domain.QPost.post;
import static com.main.suwoninfo.domain.QUser.user;

@Repository
@RequiredArgsConstructor
public class PostRepository {

    private final EntityManager entityManager;
    private final JPAQueryFactory queryFactory;

    public void post(Post post) {
        entityManager.persist(post);
    }

    public Optional<Post> findById(Long postId) {
        return queryFactory.selectFrom(post)
                .join(post.user, user).fetchJoin()
                .leftJoin(post.photo, photo).fetchJoin()
                .where(post.id.eq(postId))
                .stream().findAny();
    }

    public List<Post> findByTitle(String keyword, int limit, int offset, Post.PostType postType) {
        return queryFactory.selectFrom(post)
                .where(post.postType.eq(postType)
                        .and(post.title.like("%" + keyword + "%")
                                .or(post.content.like("%" + keyword + "%"))))
                .offset(offset)
                .limit(limit)
                .orderBy(post.id.desc())
                .fetch();
    }

    public List<Post> findByCursorPaging(int limit, int mileStoneOffset, Post.PostType postType, int pagingOffset) {

        return queryFactory.selectFrom(post)
                .join(post.user, user).fetchJoin()
                .where(post.postType.eq(postType),
                        (post.id.loe(mileStoneOffset)))
                .orderBy(post.id.desc())
                .limit(limit)
                .offset(pagingOffset)
                .fetch();
    }

    public void delete(Post post) {
        entityManager.remove(post);
    }

    public List<Post> findAllById(List<Long> longIds) {
        List<Post> rows = queryFactory.selectFrom(post)
                .join(post.user, user).fetchJoin()
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

    public List<Post> findAbsolutePaging(int limit, Post.PostType postType, int absoluteOffset) {

        return queryFactory.selectFrom(post)
                .join(post.user, user).fetchJoin()
                .where(post.postType.eq(postType))
                .orderBy(post.id.desc())
                .limit(limit)
                .offset(absoluteOffset)
                .fetch();
    }
}

