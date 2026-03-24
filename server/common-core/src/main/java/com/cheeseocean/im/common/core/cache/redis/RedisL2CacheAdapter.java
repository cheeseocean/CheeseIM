package com.cheeseocean.im.common.core.cache.redis;

import com.cheeseocean.im.common.core.cache.L2CacheAdapter;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class RedisL2CacheAdapter implements L2CacheAdapter {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisL2CacheAdapter(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
    }

    @Override
    public <T> T get(String key, Class<T> type) {
        Object value = redisTemplate.opsForValue().get(key);
        return type.isInstance(value) ? type.cast(value) : null;
    }

    @Override
    public void put(String key, Object value, Duration ttl) {
        if (ttl == null) {
            redisTemplate.opsForValue().set(key, value);
            return;
        }
        redisTemplate.opsForValue().set(key, value, ttl.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void evict(String key) {
        redisTemplate.delete(key);
    }
}
