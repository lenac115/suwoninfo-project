package com.main.suwoninfo.config;

import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.persistence.*;

@Configuration
@RequiredArgsConstructor
public class AppConfig {

    private final EntityManager entityManager;

    /**
     * querydsl 관련 설정
     * **/
    @Bean
    public JPAQueryFactory jpaQueryFactory() {
        return new JPAQueryFactory(entityManager);
    }
}