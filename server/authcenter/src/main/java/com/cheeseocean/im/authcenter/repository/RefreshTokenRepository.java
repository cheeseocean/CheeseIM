package com.cheeseocean.im.authcenter.repository;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.concurrent.TimeUnit;

@Repository
public class RefreshTokenRepository {

    private static final String REFRESH_TOKEN_PREFIX = "cheese_im:refresh_token:";

    private final RedisTemplate<String, Object> redisTemplate;

    public RefreshTokenRepository(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void save(String refreshToken, String sessionId, long ttlMs) {
        redisTemplate.opsForValue().set(REFRESH_TOKEN_PREFIX + refreshToken, sessionId, ttlMs, TimeUnit.MILLISECONDS);
    }

    public String getSessionId(String refreshToken) {
        Object value = redisTemplate.opsForValue().get(REFRESH_TOKEN_PREFIX + refreshToken);
        return value == null ? null : String.valueOf(value);
    }

    public void delete(String refreshToken) {
        redisTemplate.delete(REFRESH_TOKEN_PREFIX + refreshToken);
    }
}
