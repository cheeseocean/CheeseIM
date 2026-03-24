package com.cheeseocean.im.common.core.store.sequence.redis;

import com.cheeseocean.im.common.core.store.sequence.SequenceRange;
import com.cheeseocean.im.common.core.store.sequence.SequenceStore;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Objects;

public class RedisSequenceStore implements SequenceStore {

    private static final String KEY_PREFIX = "sequence:";

    private final StringRedisTemplate redisTemplate;

    public RedisSequenceStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
    }

    @Override
    public SequenceRange reserve(String conversationId, int size) {
        Objects.requireNonNull(conversationId, "conversationId");
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }

        Long endInclusive = redisTemplate.opsForValue().increment(KEY_PREFIX + conversationId, size);
        if (endInclusive == null) {
            throw new IllegalStateException("Failed to reserve sequence range");
        }
        long startInclusive = endInclusive - size + 1L;
        return new SequenceRange(startInclusive, endInclusive);
    }
}
