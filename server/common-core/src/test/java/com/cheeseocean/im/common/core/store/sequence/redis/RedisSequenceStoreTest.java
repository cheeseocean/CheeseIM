package com.cheeseocean.im.common.core.store.sequence.redis;

import com.cheeseocean.im.common.core.store.sequence.SequenceRange;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisSequenceStoreTest {

    @Test
    void shouldConvertRedisIncrementResultsIntoReservedRanges() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("sequence:c1", 100L)).thenReturn(100L);
        when(valueOperations.increment("sequence:c1", 50L)).thenReturn(150L);

        RedisSequenceStore store = new RedisSequenceStore(redisTemplate);

        SequenceRange first = store.reserve("c1", 100);
        SequenceRange second = store.reserve("c1", 50);

        assertEquals(1L, first.startInclusive());
        assertEquals(100L, first.endInclusive());
        assertEquals(101L, second.startInclusive());
        assertEquals(150L, second.endInclusive());
    }
}
