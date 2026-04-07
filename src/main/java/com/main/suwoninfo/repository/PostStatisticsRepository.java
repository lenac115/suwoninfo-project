package com.main.suwoninfo.repository;

import com.main.suwoninfo.domain.Post;
import com.main.suwoninfo.domain.PostStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PostStatisticsRepository extends JpaRepository<PostStatistics, Long> {

    PostStatistics findByPostType(Post.PostType postType);
}
