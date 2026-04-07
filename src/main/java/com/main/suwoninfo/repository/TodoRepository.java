package com.main.suwoninfo.repository;

import com.main.suwoninfo.domain.Todo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Optional;

@Repository
public interface TodoRepository extends JpaRepository<Todo, Long> {

    @Query("select t from Todo t where t.user.email = :email")
    List<Todo> findByUser(String email);

    Optional<Todo> findById(@Nonnull Long id);

    void createTodo(Todo todo);

    void delete(@Nonnull Todo todo);
}
