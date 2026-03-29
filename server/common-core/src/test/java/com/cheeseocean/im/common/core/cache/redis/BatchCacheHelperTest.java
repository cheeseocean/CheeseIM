package com.cheeseocean.im.common.core.cache.redis;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BatchCacheHelperTest {

    @Test
    void batchGetCache2ShouldLoadMissesFillCacheAndPreserveInputOrder() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        Duration ttl = Duration.ofMinutes(5);
        DemoValue cached = new DemoValue("u1", "cached");
        DemoValue loaded = new DemoValue("u2", "loaded");
        when(valueOps.multiGet(List.of("demo:u1", "demo:u2"))).thenReturn(Arrays.asList(cached, null));

        List<DemoValue> results = BatchCacheHelper.batchGetCache2(
                redisTemplate,
                ttl,
                List.of("u1", "u2"),
                id -> "demo:" + id,
                DemoValue::id,
                ids -> {
                    assertThat(ids).containsExactly("u2");
                    return List.of(loaded);
                },
                DemoValue.class
        );

        assertThat(results).containsExactly(cached, loaded);
        verify(valueOps).set("demo:u2", loaded, ttl);
        verify(valueOps, never()).set(eq("demo:u1"), eq(cached), eq(ttl));
    }

    @Test
    void batchGetCache2ShouldDeduplicateMissIdsBeforeCallingLoader() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        Duration ttl = Duration.ofMinutes(5);
        when(valueOps.multiGet(List.of("demo:u1", "demo:u1", "demo:u2"))).thenReturn(Arrays.asList(null, null, null));

        AtomicReference<List<String>> loadedIds = new AtomicReference<>();

        List<DemoValue> results = BatchCacheHelper.batchGetCache2(
                redisTemplate,
                ttl,
                List.of("u1", "u1", "u2"),
                id -> "demo:" + id,
                DemoValue::id,
                ids -> {
                    loadedIds.set(List.copyOf(ids));
                    return List.of(new DemoValue("u1", "first"), new DemoValue("u2", "second"));
                },
                DemoValue.class
        );

        assertThat(loadedIds.get()).containsExactly("u1", "u2");
        assertThat(results).containsExactly(
                new DemoValue("u1", "first"),
                new DemoValue("u1", "first"),
                new DemoValue("u2", "second")
        );
        verify(valueOps).set("demo:u1", new DemoValue("u1", "first"), ttl);
        verify(valueOps).set("demo:u2", new DemoValue("u2", "second"), ttl);
    }

    @Test
    void getCacheShouldReturnCachedValueWithoutCallingLoader() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        DemoValue cached = new DemoValue("u1", "cached");
        when(valueOps.get("demo:u1")).thenReturn(cached);

        AtomicInteger loaderCalls = new AtomicInteger();

        Optional<DemoValue> result = BatchCacheHelper.getCache(
                redisTemplate,
                "demo:u1",
                Duration.ofMinutes(5),
                () -> {
                    loaderCalls.incrementAndGet();
                    return Optional.of(new DemoValue("u1", "loaded"));
                },
                DemoValue.class
        );

        assertThat(result).contains(cached);
        assertThat(loaderCalls.get()).isZero();
    }

    private record DemoValue(String id, String value) {
    }
}
