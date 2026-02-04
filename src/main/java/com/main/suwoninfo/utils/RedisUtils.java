package com.main.suwoninfo.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RedisUtils {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisTemplate<String, Object> redisBlackListTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    public void set(String key, Object o, Duration minutes) {
        redisTemplate.setValueSerializer(new Jackson2JsonRedisSerializer(o.getClass()));
        redisTemplate.opsForValue().set(key, o, minutes);
    }

    public List<String> listSet(String key, int start, int end) {
        List<String> r = stringRedisTemplate.opsForList().range(key, start, end);
        return (r != null) ? r : Collections.emptyList();
    }

    public void stringSet(String key, String value, Duration minutes) {
        stringRedisTemplate.opsForValue().set(key, value, minutes);
    }

    public Boolean setIfAbsent(String key, Object o, Duration seconds) {
        return redisTemplate.opsForValue().setIfAbsent(key, o, seconds);
    }

    public void listRightPush(String key, List<String> list) {
        stringRedisTemplate.opsForList().rightPushAll(key, list);
    }

    public void expire(String key, Duration seconds) {
        redisTemplate.expire(key, seconds);
    }

    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public Object stringGet (String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    public boolean delete(String key) {
        return Boolean.TRUE.equals(redisTemplate.delete(key));
    }

    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public void setBlackList(String key, Object o, Duration minutes) {
        redisBlackListTemplate.setValueSerializer(new Jackson2JsonRedisSerializer(o.getClass()));
        redisBlackListTemplate.opsForValue().set(key, o, minutes);
    }

    public Object getBlackList(String key) {
        return redisBlackListTemplate.opsForValue().get(key);
    }

    public boolean deleteBlackList(String key) {
        return Boolean.TRUE.equals(redisBlackListTemplate.delete(key));
    }

    public boolean hasKeyBlackList(String key) {
        return Boolean.TRUE.equals(redisBlackListTemplate.hasKey(key));
    }

    public List<Object> multiGet(List<String> postKeys) {
        return redisTemplate.opsForValue().multiGet(postKeys);
    }
}
