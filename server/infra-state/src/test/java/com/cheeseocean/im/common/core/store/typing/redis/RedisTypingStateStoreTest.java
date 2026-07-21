package com.cheeseocean.im.common.core.store.typing.redis;

import com.cheeseocean.im.common.api.enums.TypingActionEnum;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisTypingStateStoreTest {

    @Test
    void startShouldUseAtomicNxTtlScript() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(), anyList(), any(Object[].class))).thenReturn(1L);
        RedisTypingStateStore store = new RedisTypingStateStore(redisTemplate);

        assertTrue(store.update("u1", "s:u1:u2", TypingActionEnum.START, 4));

        verify(redisTemplate).execute(eq(RedisTypingStateStore.UPDATE_SCRIPT),
                eq(List.of("typing:state:{s:u1:u2}:u1")), eq("START"), eq("4"));
    }

    @Test
    void repeatedStartShouldBeSuppressed() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(), anyList(), any(Object[].class))).thenReturn(0L);
        RedisTypingStateStore store = new RedisTypingStateStore(redisTemplate);

        assertFalse(store.update("u1", "s:u1:u2", TypingActionEnum.START, 4));
    }

    @Test
    void stopShouldUseSameAtomicStateScript() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(), anyList(), any(Object[].class))).thenReturn(1L);
        RedisTypingStateStore store = new RedisTypingStateStore(redisTemplate);

        assertTrue(store.update("u1", "s:u1:u2", TypingActionEnum.STOP, 4));

        verify(redisTemplate).execute(eq(RedisTypingStateStore.UPDATE_SCRIPT),
                eq(List.of("typing:state:{s:u1:u2}:u1")), eq("STOP"), eq("4"));
    }
}
