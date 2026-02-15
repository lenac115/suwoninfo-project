package com.main.suwoninfo.repository;

import com.main.suwoninfo.domain.Post;
import com.main.suwoninfo.domain.PostStatistics;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;


import static com.main.suwoninfo.domain.QPostStatistics.postStatistics;

@RequiredArgsConstructor
@Repository
public class PostStatisticsRepository {

    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;

    public Integer countPost(Post.PostType postType) {

        return queryFactory.select(postStatistics.count)
                .from(postStatistics)
                .where(postStatistics.postType.eq(postType))
                .fetchOne();
    }

    public PostStatistics findByType(Post.PostType postType) {

        return queryFactory.selectFrom(postStatistics)
                .where(postStatistics.postType.eq(postType))
                .fetchOne();
    }
}
