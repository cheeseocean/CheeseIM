package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.core.constants.RedisKeys;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationReceiptServiceTest {

    @Test
    void applyReadCursorShouldWriteUserReadSeq() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        ConversationReceiptService service = new ConversationReceiptService(redisTemplate);

        service.applyReadCursor("userB", "c1:userA:userB", 19L);

        verify(valueOperations).set(eq(RedisKeys.userReadSeq("userB", "c1:userA:userB")), eq("19"));
    }

    @Test
    void applyReadCursorShouldRejectMissingSeq() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ConversationReceiptService service = new ConversationReceiptService(redisTemplate);

        assertThrows(IllegalArgumentException.class,
                () -> service.applyReadCursor("userB", "c1:userA:userB", null));
    }
}
