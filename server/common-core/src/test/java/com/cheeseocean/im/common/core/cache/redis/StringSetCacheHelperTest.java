package com.cheeseocean.im.common.core.cache.redis;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StringSetCacheHelperTest {

    @Test
    void shouldReturnCachedMembersWithoutCallingLoader() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, Object> setOps = mock(SetOperations.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(setOps.members("set:key")).thenReturn(Set.of("c1", "c2"));

        AtomicInteger loaderCalls = new AtomicInteger();

        List<String> values = StringSetCacheHelper.getOrLoad(
                redisTemplate,
                "set:key",
                "set:key:loaded",
                () -> {
                    loaderCalls.incrementAndGet();
                    return List.of("db1");
                }
        );

        assertThat(values).containsExactlyInAnyOrder("c1", "c2");
        assertThat(loaderCalls.get()).isZero();
        verify(redisTemplate, never()).hasKey("set:key:loaded");
    }

    @Test
    void shouldReturnEmptyWhenLoadedMarkerExistsForEmptySet() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, Object> setOps = mock(SetOperations.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(setOps.members("set:key")).thenReturn(Set.of());
        when(redisTemplate.hasKey("set:key:loaded")).thenReturn(true);

        AtomicInteger loaderCalls = new AtomicInteger();

        List<String> values = StringSetCacheHelper.getOrLoad(
                redisTemplate,
                "set:key",
                "set:key:loaded",
                () -> {
                    loaderCalls.incrementAndGet();
                    return List.of("db1");
                }
        );

        assertThat(values).isEmpty();
        assertThat(loaderCalls.get()).isZero();
    }

    @Test
    void shouldLoadFromDbAndMarkEmptySetAsLoaded() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, Object> setOps = mock(SetOperations.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(setOps.members("set:key")).thenReturn(null);
        when(redisTemplate.hasKey("set:key:loaded")).thenReturn(false);

        List<String> values = StringSetCacheHelper.getOrLoad(
                redisTemplate,
                "set:key",
                "set:key:loaded",
                List::of
        );

        assertThat(values).isEmpty();
        verify(redisTemplate).delete("set:key");
        verify(valueOps).set("set:key:loaded", true);
        verify(setOps, never()).add("set:key");
    }

    @Test
    void containsOrLoadShouldReturnFalseWhenLoadedMarkerExistsAndMemberMissing() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, Object> setOps = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.isMember("set:key", "u1")).thenReturn(false);
        when(redisTemplate.hasKey("set:key:loaded")).thenReturn(true);

        AtomicInteger loaderCalls = new AtomicInteger();

        boolean result = StringSetCacheHelper.containsOrLoad(
                redisTemplate,
                "set:key",
                "set:key:loaded",
                "u1",
                () -> {
                    loaderCalls.incrementAndGet();
                    return true;
                }
        );

        assertThat(result).isFalse();
        assertThat(loaderCalls.get()).isZero();
    }

    @Test
    void containsOrLoadShouldQueryDbAndBackfillMemberWhenNotLoaded() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, Object> setOps = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.isMember("set:key", "u1")).thenReturn(false);
        when(redisTemplate.hasKey("set:key:loaded")).thenReturn(false);

        boolean result = StringSetCacheHelper.containsOrLoad(
                redisTemplate,
                "set:key",
                "set:key:loaded",
                "u1",
                () -> true
        );

        assertThat(result).isTrue();
        verify(setOps).add("set:key", "u1");
    }

    @Test
    void containsOrLoadShouldNotBackfillWhenDbMisses() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, Object> setOps = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.isMember("set:key", "u1")).thenReturn(false);
        when(redisTemplate.hasKey("set:key:loaded")).thenReturn(false);

        boolean result = StringSetCacheHelper.containsOrLoad(
                redisTemplate,
                "set:key",
                "set:key:loaded",
                "u1",
                () -> false
        );

        assertThat(result).isFalse();
        verify(setOps, never()).add(eq("set:key"), eq("u1"));
    }
}
