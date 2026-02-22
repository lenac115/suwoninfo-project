package com.main.suwoninfo.repository;

import com.main.suwoninfo.domain.User;
import com.querydsl.core.QueryFactory;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.main.suwoninfo.domain.QUser.user;


@Repository
@RequiredArgsConstructor
public class UserRepository {

    private final EntityManager entityManager;
    private final JPAQueryFactory queryFactory;


    public void save(User user) {
        entityManager.persist(user);
    }

    public Optional<User> findById(Long id) {
        return queryFactory.selectFrom(user)
                .where(user.id.eq(id).and(user.activated.eq(true)))
                .stream().findAny();
    }

    public Optional<User> findByEmail(String email) {
        return queryFactory.selectFrom(user)
                .where(user.email.eq(email).and(user.activated.eq(true)))
                .stream().findAny();
    }

    public Optional<User> findByNick(String nickName) {
        return queryFactory.selectFrom(user)
                .where(user.nickname.eq(nickName).and(user.activated.eq(true)))
                .stream().findAny();
    }
}
