package com.main.suwoninfo.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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

    public void increment(String key) {
        stringRedisTemplate.opsForValue().increment(key);
    }

    public void decrement(String key) {
        stringRedisTemplate.opsForValue().decrement(key);
    }

    public Boolean setIfAbsent(String key, Object o, Duration seconds) {
        return redisTemplate.opsForValue().setIfAbsent(key, o, seconds);
    }

    public void expire(String key, Duration seconds) {
        redisTemplate.expire(key, seconds);
    }

    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
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
        if (postKeys == null || postKeys.isEmpty()) {
            return Collections.emptyList();
        }

        return redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                    StringRedisSerializer keySerializer = (StringRedisSerializer) redisTemplate.getKeySerializer();

                    for (String key : postKeys) {
                        connection.stringCommands().get(keySerializer.serialize(key));
                    }
                    return null;
                }
        );
    }


    public Long getTtl(String key) {
        return redisTemplate.getExpire(key, TimeUnit.SECONDS);
    }

    public void pipelineSet(Map<String, Object> keyValues, Duration ttl) {
        RedisSerializer<Object> valueSerializer = (RedisSerializer<Object>) redisTemplate.getValueSerializer();

        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            keyValues.forEach((key, value) -> {
                try {
                    byte[] keyBytes = key.getBytes();
                    byte[] valueBytes = valueSerializer.serialize(value);

                    if (valueBytes != null) {
                        connection.stringCommands().set(keyBytes, valueBytes);
                        connection.keyCommands().expire(keyBytes, ttl.getSeconds());
                    }
                } catch (Exception e) {
                    // 파이프라인 내에서는 예외를 던지지 않고 로깅만
                    System.err.println("파이프라인 set 실패: key=" + key + ", error=" + e.getMessage());
                }
            });
            return null;
        });
    }

    public Long pipelineDelete(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return 0L;
        }
        return redisTemplate.delete(keys);
    }

    public String versionedKey(String version, String key) {
        return version + ":" + key;
    }

    public void zSetSet(String key, Long o) {
        stringRedisTemplate.opsForZSet().add(key, String.valueOf(o), o);
    }

    public Long zSetGetCount(String key, Long min, Long max) {
        return stringRedisTemplate.opsForZSet().count(key, min, max);
    }


    public void invalidateVersion(String version) {
        List<String> keysToDelete = keys(version + ":*");
        if (!keysToDelete.isEmpty()) {
            pipelineDelete(keysToDelete);
        }
    }

    public List<String> listSet(String key, int start, int end) {
        List<String> r = stringRedisTemplate.opsForList().range(key, start, end);
        return (r != null) ? r : Collections.emptyList();
    }

    public void stringSet(String key, String value, Duration minutes) {
        stringRedisTemplate.opsForValue().set(key, value, minutes);
    }

    public void stringSet(String key, String value) {
        stringRedisTemplate.opsForValue().set(key, value);
    }


    public void listRightPush(String key, List<String> list) {
        stringRedisTemplate.opsForList().rightPushAll(key, list);
    }

    public String stringGet(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    public List<String> keys(String pattern) {
        return stringRedisTemplate.keys(pattern)
                .stream()
                .toList();
    }

    public void zSetDelete(String s) {
        stringRedisTemplate.opsForZSet().remove(s);
    }
}
