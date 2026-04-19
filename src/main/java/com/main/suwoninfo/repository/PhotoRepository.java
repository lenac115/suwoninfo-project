package com.main.suwoninfo.repository;

import com.main.suwoninfo.domain.Photo;
import com.main.suwoninfo.domain.QPhoto;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface PhotoRepository extends JpaRepository<Photo, Long> {
    Photo save(Photo photo);

    void delete(Photo photo);

    @Query("select p from Photo p where p.post.id = :postId")
    List<Photo> findByPost(Long postId);

    Optional<Photo> findById(Long id);
}
