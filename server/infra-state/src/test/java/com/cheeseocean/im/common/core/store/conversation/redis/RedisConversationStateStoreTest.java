package com.cheeseocean.im.common.core.store.conversation.redis;

import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.common.core.store.conversation.ConversationStateStore;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class RedisConversationStateStoreTest {

    @Test
    void advanceUserMaxShouldAtomicallyUpdateMaxAndUnread() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisConversationStateStore store = new RedisConversationStateStore(redisTemplate);

        store.advanceUserMaxSeq("u1", "g:crew", 11L, true);

        verify(redisTemplate).execute(any(), eq(List.of(
                RedisKeys.userMaxSeq("u1", "g:crew"), RedisKeys.userUnread("u1", "g:crew"))),
                eq("11"), eq("1"));
    }

    @Test
    void advanceReadStateShouldPassAllKeysAndBootstrapValuesToSingleScript() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        List<String> keys = List.of(
                RedisKeys.userReadSeq("u1", "g:crew"),
                RedisKeys.userMaxSeq("u1", "g:crew"),
                RedisKeys.userUnread("u1", "g:crew"));
        when(redisTemplate.execute(any(), eq(keys), eq("9"), eq("4"), eq("8")))
                .thenReturn(List.of(8L, 0L, 1L));

        ConversationStateStore.ReadState result = new RedisConversationStateStore(redisTemplate)
                .advanceReadState("u1", "g:crew", 9L, 4L, 8L);

        assertEquals(8L, result.readSeq());
        assertEquals(0, result.unread());
        assertTrue(result.changed());
    }

    @Test
    void advanceReadStateShouldReturnActualUnchangedCursor() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(), any(), eq("6"), eq("7"), eq("10")))
                .thenReturn(List.of(7L, 3L, 0L));

        ConversationStateStore.ReadState result = new RedisConversationStateStore(redisTemplate)
                .advanceReadState("u1", "g:crew", 6L, 7L, 10L);

        assertEquals(7L, result.readSeq());
        assertEquals(3, result.unread());
        assertFalse(result.changed());
    }
}
