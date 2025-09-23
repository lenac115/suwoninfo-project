package com.main.suwoninfo.repository;

import com.main.suwoninfo.domain.User;
import com.main.suwoninfo.domain.UserAuthority;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
@RequiredArgsConstructor
public class UserRepository {

    private final EntityManager entityManager;


    public void save(User user) {
        entityManager.persist(user);
    }

    public void authSave(UserAuthority userAuthority) {
        entityManager.persist(userAuthority);
    }

    public Optional<User> findById(Long id) {
        return entityManager.createQuery("select u from User u where u.id = :id", User.class)
                .setParameter("id", id)
                .getResultList().stream().findAny();
    }

    public Optional<User> findByEmail(String email) {
        return entityManager.createQuery("select u from User u where u.email = :email", User.class)
                .setParameter("email", email)
                .getResultList().stream().findAny();
    }

    public Optional<User> findByNick(String nickName) {
        return entityManager.createQuery("select u from User u where u.nickname = :nickname", User.class)
                .setParameter("nickname", nickName)
                .getResultList().stream().findAny();
    }

    public List<String> findAuthById(String email) {
        return entityManager.createQuery("select a.authority.name from UserAuthority a where a.user.email = :email", String.class)
                .setParameter("email", email)
                .getResultList();
    }

    public List<User> findAll() {
        return entityManager.createQuery("select u from User u")
                .getResultList();
    }

    public void delete(User user) {
        user.setActivated(false);
    }
}
