package com.main.suwoninfo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.module.blackbird.BlackbirdModule;
import com.main.suwoninfo.domain.Post;
import com.main.suwoninfo.redis.RedisConnectedEvent;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.event.connection.ConnectionActivatedEvent;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scheduling.annotation.Async;

import java.time.Duration;

/**
 * redis 관련 설정
 */
@RequiredArgsConstructor
@Configuration
@EnableRedisRepositories
@Slf4j
public class RedisConfig {

    //프로퍼티스에서 설정한 값을 host로 설정
    @Value("${spring.data.redis.host}")
    private String host;

    //프로퍼티스에서 설정한 포트값을 port로 설정
    @Value("${spring.data.redis.port}")
    private int port;

    //host값과 port값을 configuration에 넣어서 빈에 올림
    @Bean
    public RedisConnectionFactory redisConnectionFactory(ClientResources clientResources) {
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .clientOptions(ClientOptions.builder()
                        .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)  // 연결이 없으면 바로 명령어 거절
                        .build())
                .commandTimeout(Duration.ofSeconds(3))
                .clientResources(clientResources)
                .build();

        RedisStandaloneConfiguration serverConfig = new RedisStandaloneConfiguration(host, port);

        return new LettuceConnectionFactory(serverConfig, clientConfig);
    }

    @Bean
    ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .addModule(new BlackbirdModule())
                .build();
    }

    //string과 다른 객체가 들어간경우 사용되는 템플릿
    @Bean
    public RedisTemplate<String, Object> generalRedisTemplate(RedisConnectionFactory cf, ObjectMapper objectMapper) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(cf);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer(objectMapper));
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashValueSerializer(new GenericJackson2JsonRedisSerializer(objectMapper));

        return redisTemplate;
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory cf) {
        return new StringRedisTemplate(cf);
    }

    @Bean
    public CacheManager cacheManager(LettuceConnectionFactory cf) {
        RedisCacheConfiguration cfg = RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .entryTtl(Duration.ofMinutes(10));

        return RedisCacheManager.builder(cf).cacheDefaults(cfg).build();
    }

    @Bean(destroyMethod = "shutdown")
    public ClientResources clientResources(ApplicationEventPublisher eventPublisher) {
        DefaultClientResources defaultClientResources = DefaultClientResources.create();

        defaultClientResources.eventBus().get().subscribe(event -> {
                    log.info("Lettuce Event 발생: {}", event.getClass().getSimpleName());
                    try {
                        if (event instanceof ConnectionActivatedEvent) {
                            eventPublisher.publishEvent(new RedisConnectedEvent(this));
                        }
                    } catch (Exception e) {
                        log.error("Spring Event 발행 중 에러 발생! (스트림은 유지됩니다)", e);
                    }
                },
                error -> log.error("EventBus 스트림에서 치명적 에러 발생 (구독 해지됨)", error)
        );

        return defaultClientResources;
    }
}