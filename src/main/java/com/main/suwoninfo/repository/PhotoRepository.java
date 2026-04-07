package com.main.suwoninfo.repository;

import com.main.suwoninfo.domain.Photo;
import com.main.suwoninfo.domain.QPhoto;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface PhotoRepository extends JpaRepository<Photo, Long> {
    Photo save(Photo photo);

    void delete(Photo photo);

    List<Photo> findByPost(Long postId);

    Optional<Photo> findById(Long id);
}
