package com.cheeseocean.im.postman.service;

import com.cheeseocean.im.common.core.constants.RedisKeys;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationSeqServiceTest {

    @Test
    void nextSeqShouldIncrementConversationCounterInRedis() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(RedisKeys.convMaxSeq("c1:userA:userB"))).thenReturn(1001L);

        ConversationSeqService service = new ConversationSeqService(redisTemplate);

        assertEquals(1001L, service.nextSeq("c1:userA:userB"));
    }

    @Test
    void nextSeqShouldFailWhenRedisDoesNotReturnAValue() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(RedisKeys.convMaxSeq("c1:userA:userB"))).thenReturn(null);

        ConversationSeqService service = new ConversationSeqService(redisTemplate);

        assertThrows(IllegalStateException.class, () -> service.nextSeq("c1:userA:userB"));
    }
}
