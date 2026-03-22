package com.cheeseocean.im.postman.service;

import com.cheeseocean.im.common.core.constants.RedisKeys;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsumerDedupServiceTest {

    @Test
    void shouldStoreConsumerDedupMarkerUnderCoreRedisKey() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(RedisKeys.consumerDedup("postman-receipt", "evt-1")), eq("1"), eq(Duration.ofHours(24))))
                .thenReturn(true);

        ConsumerDedupService service = new ConsumerDedupService(redisTemplate);

        assertTrue(service.markIfAbsent("postman-receipt", "evt-1"));
        verify(valueOperations).setIfAbsent(eq(RedisKeys.consumerDedup("postman-receipt", "evt-1")), eq("1"), eq(Duration.ofHours(24)));
    }
}
