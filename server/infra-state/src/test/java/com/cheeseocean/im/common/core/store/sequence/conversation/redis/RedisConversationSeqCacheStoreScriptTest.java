package com.cheeseocean.im.common.core.store.sequence.conversation.redis;

import com.cheeseocean.im.common.core.store.sequence.conversation.ConversationSeqCacheResult;
import com.cheeseocean.im.common.core.store.sequence.conversation.ConversationSeqRangeState;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisConversationSeqCacheStoreScriptTest {

    @Test
    void allocateShouldMapMissState() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(), anyList(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(1L, "owner-1", 100L));

        RedisConversationSeqCacheStore store = new RedisConversationSeqCacheStore(redisTemplate, 3L, 60L);

        ConversationSeqCacheResult result = store.allocate("s:u100:u200", 2, 100L);

        assertEquals(ConversationSeqRangeState.MISS, result.state());
        assertEquals("owner-1", result.ownerToken());
        assertEquals(100L, result.timestampMillis());
    }

    @Test
    void allocateShouldMapAllocatedState() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(), anyList(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(0L, 10L, 50L, 100L));

        RedisConversationSeqCacheStore store = new RedisConversationSeqCacheStore(redisTemplate, 3L, 60L);

        ConversationSeqCacheResult result = store.allocate("s:u100:u200", 2, 100L);

        assertEquals(ConversationSeqRangeState.ALLOCATED, result.state());
        assertEquals(10L, result.currentSeq());
        assertEquals(50L, result.lastSeq());
    }
}
