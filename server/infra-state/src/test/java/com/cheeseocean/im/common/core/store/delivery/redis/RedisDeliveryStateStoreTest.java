package com.cheeseocean.im.common.core.store.delivery.redis;

import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.common.core.store.delivery.DeliveryStateStore;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisDeliveryStateStoreTest {
    @Test
    void shouldReturnActualMonotonicDeviceHighWatermarkFromLua() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(), eq(List.of(RedisKeys.deviceDeliveredSeq("u2", "ios-1", "s:u1:u2"))),
                eq("8"), eq("2592000")))
                .thenReturn(List.of(10L, 0L));

        DeliveryStateStore.AdvanceResult result = new RedisDeliveryStateStore(redis)
                .advance("u2", "ios-1", "s:u1:u2", 8L);

        assertEquals(10L, result.deliveredSeq());
        assertFalse(result.changed());
    }
}
