package com.main.suwoninfo.repository;

import com.main.suwoninfo.domain.Comment;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.main.suwoninfo.domain.QComment.comment;

@Repository
@RequiredArgsConstructor
public class CommentQueryRepository {

    private final JPAQueryFactory queryFactory;

    public List<Comment> findByPaging(Long id) {
        return queryFactory.selectFrom(comment)
                .leftJoin(comment.parent)
                .fetchJoin()
                .where(comment.post.id.eq(id))
                .orderBy(comment.createdTime.asc(), comment.parent.id.asc().nullsFirst())
                .fetch();
    }
}
