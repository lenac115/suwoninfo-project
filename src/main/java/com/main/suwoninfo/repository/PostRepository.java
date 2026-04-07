package com.main.suwoninfo.repository;

import com.main.suwoninfo.domain.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    @Query("select count(p) from Post p where p.postType = :type")
    Integer countPostByPostType(String type);
}
