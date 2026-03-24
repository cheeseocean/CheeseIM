package com.cheeseocean.im.common.core.store.idempotency.redis;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisIdempotencyStoreTest {

    @Test
    void shouldDelegatePutIfAbsentWithTtlToRedis() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("dup:1", "1", Duration.ofMinutes(1))).thenReturn(true);
        when(valueOperations.setIfAbsent("dup:2", "1", Duration.ofMinutes(1))).thenReturn(false);

        RedisIdempotencyStore store = new RedisIdempotencyStore(redisTemplate);

        assertTrue(store.putIfAbsent("dup:1", Duration.ofMinutes(1)));
        assertFalse(store.putIfAbsent("dup:2", Duration.ofMinutes(1)));
    }
}
