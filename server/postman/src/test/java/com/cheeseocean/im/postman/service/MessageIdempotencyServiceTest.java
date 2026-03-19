package com.cheeseocean.im.postman.service;

import com.cheeseocean.im.common.dto.DeliveryResult;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageIdempotencyServiceTest {

    @Test
    void duplicateClientMsgIdShouldReturnExistingServerMsgId() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("cheese_im:delivery:idempotency:userA:single:userA:userB:c-1"))
                .thenReturn("s-1|ONLINE_CONFIRMED");

        MessageIdempotencyService service = new MessageIdempotencyService(redisTemplate);

        Optional<DeliveryResult> existing = service.findExisting("userA", "single:userA:userB", "c-1");

        assertTrue(existing.isPresent());
        assertEquals("s-1", existing.get().getServerMsgId());
        assertEquals("ONLINE_CONFIRMED", existing.get().getStatus());
    }

    @Test
    void rememberShouldStoreExistingOutcomeWithTtl() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        MessageIdempotencyService service = new MessageIdempotencyService(redisTemplate);
        DeliveryResult result = DeliveryResult.onlineSuccess("s-1");

        service.remember("userA", "single:userA:userB", "c-1", result);

        verify(valueOperations).set(eq("cheese_im:delivery:idempotency:userA:single:userA:userB:c-1"),
                eq("s-1|ONLINE_CONFIRMED"), anyLong(), eq(TimeUnit.HOURS));
    }
}
