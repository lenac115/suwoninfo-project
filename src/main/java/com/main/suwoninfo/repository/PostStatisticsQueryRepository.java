package com.main.suwoninfo.repository;

import com.main.suwoninfo.domain.Post;
import com.main.suwoninfo.domain.PostStatistics;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import static com.main.suwoninfo.domain.QPostStatistics.postStatistics;

@Repository
@RequiredArgsConstructor
public class PostStatisticsQueryRepository {

    private final PostRepository postRepository;
    private final PostStatisticsRepository postStatisticsRepository;
    private final JPAQueryFactory queryFactory;

    public int countPost(Post.PostType postType) {

        Integer cached = queryFactory.select(postStatistics.count)
                .from(postStatistics)
                .where(postStatistics.postType.eq(postType))
                .fetchOne();

        if (cached == null) {
            Integer counting = postRepository.countPostByPostType("TRADE");
            postStatisticsRepository.save(PostStatistics.builder()
                    .count(counting).postType(Post.PostType.TRADE).build());

            return counting;
        }

        return cached;
    }
}
