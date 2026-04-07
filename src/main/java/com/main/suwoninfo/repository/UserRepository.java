package com.main.suwoninfo.repository;

import com.main.suwoninfo.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import javax.annotation.Nonnull;
import java.util.Optional;


@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    User save(User user);

    Optional<User> findById(@Nonnull Long id);

    Optional<User> findByEmail(String email);

    Optional<User> findByNickname(String nickName);
}
