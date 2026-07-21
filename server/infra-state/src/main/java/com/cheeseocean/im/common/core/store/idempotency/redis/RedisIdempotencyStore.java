package com.cheeseocean.im.common.core.store.idempotency.redis;

import com.cheeseocean.im.common.core.store.idempotency.IdempotencyStore;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Objects;

public class RedisIdempotencyStore implements IdempotencyStore {

    private final StringRedisTemplate redisTemplate;

    public RedisIdempotencyStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
    }

    @Override
    public boolean putIfAbsent(String key, Duration ttl) {
        Boolean inserted = redisTemplate.opsForValue().setIfAbsent(key, "1", ttl);
        return Boolean.TRUE.equals(inserted);
    }
}
