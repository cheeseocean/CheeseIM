package com.cheeseocean.im.postmaster.service;

import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.common.core.store.idempotency.IdempotencyStore;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsumerDedupServiceTest {

    @Test
    void shouldStoreConsumerDedupMarkerUnderCoreRedisKey() {
        IdempotencyStore idempotencyStore = mock(IdempotencyStore.class);
        when(idempotencyStore.putIfAbsent(eq(RedisKeys.consumerDedup("postman-receipt", "evt-1")), eq(Duration.ofHours(24))))
                .thenReturn(true);

        ConsumerDedupService service = new ConsumerDedupService(idempotencyStore);

        assertTrue(service.markIfAbsent("postman-receipt", "evt-1"));
        verify(idempotencyStore).putIfAbsent(eq(RedisKeys.consumerDedup("postman-receipt", "evt-1")), eq(Duration.ofHours(24)));
    }
}
