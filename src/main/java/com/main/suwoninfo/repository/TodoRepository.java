package com.main.suwoninfo.repository;

import com.main.suwoninfo.domain.Todo;
import com.main.suwoninfo.domain.User;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class TodoRepository {

    private final EntityManager entityManager;
    private final JPAQueryFactory queryFactory;

    public List<Todo> findByUser(String email) {
        return entityManager.createQuery("select t from Todo t where t.user.email = :email", Todo.class)
                .setParameter("email", email)
                .getResultList();
    }

    public Optional<Todo> findById(Long id) {
        return entityManager.createQuery("select t from Todo t where t.id = :id", Todo.class)
                .setParameter("id", id)
                .getResultList().stream().findAny();
    }

    public void createTodo(Todo todo) {
        entityManager.persist(todo);
    }

    public void delete(Todo todo) {
        entityManager.remove(todo);
    }
}
