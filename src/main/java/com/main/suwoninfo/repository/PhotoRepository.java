package com.main.suwoninfo.repository;

import com.main.suwoninfo.domain.Photo;
import com.main.suwoninfo.domain.QPhoto;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@RequiredArgsConstructor
@Repository
public class PhotoRepository {

    private final EntityManager entityManager;
    private final JPAQueryFactory queryFactory;

    public Photo save(Photo photo) {
        entityManager.persist(photo);
        return photo;
    }

    public void delete(Photo photo) {
        entityManager.remove(photo);
    }

    public List<Photo> findByPost(Long postId) {
        return entityManager.createQuery("select p.photo from Post p where p.id = :id", Photo.class)
                .setParameter("id", postId)
                .getResultList();
    }

    public Optional<Photo> findById(Long id) {
        return queryFactory.selectFrom(QPhoto.photo)
                .where(QPhoto.photo.id.eq(id))
                .fetch().stream().findAny();
    }
}
