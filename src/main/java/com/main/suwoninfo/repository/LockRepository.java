package com.main.suwoninfo.repository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class LockRepository {

    private final EntityManager entityManager;

    public Boolean getLock(String key, int timeoutSeconds) {

        String sql = "SELECT GET_LOCK(:key, :timeout)";
        Number result = (Number) entityManager.createNativeQuery(sql)
                .setParameter("key", key)
                .setParameter("timeout", timeoutSeconds)
                .getSingleResult();

        return result != null && result.intValue() == 1;
    }

    public Boolean releaseLock(String key) {
        String sql = "SELECT RELEASE_LOCK(:key)";

        Integer result = Integer.parseInt(String.valueOf(entityManager.createNativeQuery(sql)
                .setParameter("key", key)
                .getSingleResult()));
        return result == 1;
    }

    public Boolean isUsedLock(String key) {
        String sql = "SELECT IS_USED_LOCK(:key)";
        return entityManager.createNativeQuery(sql)
                .setParameter("key", key)
                .getSingleResult() != null;
    }
}
