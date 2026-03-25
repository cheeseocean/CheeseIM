package com.cheeseocean.im.postmaster.service;

import com.cheeseocean.im.common.api.dto.message.DeliveryResult;
import com.cheeseocean.im.common.core.cache.MultiLevelCacheService;
import com.cheeseocean.im.common.core.constants.RedisKeys;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageIdempotencyServiceTest {

    @Test
    void duplicateClientMsgIdShouldReturnExistingServerMsgId() {
        MultiLevelCacheService cacheService = mock(MultiLevelCacheService.class);
        when(cacheService.getOrLoad(eq(RedisKeys.postmanIdem("c1:userA:userB", "c-1")), eq(String.class), eq(Duration.ofHours(24)), any()))
                .thenReturn("s-1|ACCEPTED|1001|INIT");

        MessageIdempotencyService service = new MessageIdempotencyService(cacheService);

        Optional<DeliveryResult> existing = service.findExisting("userA", "c1:userA:userB", "c-1");

        assertTrue(existing.isPresent());
        assertEquals("s-1", existing.get().getServerMsgId());
        assertEquals("ACCEPTED", existing.get().getStatus());
        assertEquals(1001L, existing.get().getConversationSeq());
    }

    @Test
    void rememberShouldStoreExistingOutcomeWithTtl() {
        MultiLevelCacheService cacheService = mock(MultiLevelCacheService.class);

        MessageIdempotencyService service = new MessageIdempotencyService(cacheService);
        DeliveryResult result = DeliveryResult.onlineSuccess("s-1");

        service.remember("userA", "c1:userA:userB", "c-1", result);

        verify(cacheService).put(eq(RedisKeys.postmanIdem("c1:userA:userB", "c-1")),
                eq("s-1|ONLINE_CONFIRMED||ONLINE_CONFIRMED"), eq(Duration.ofHours(24)));
    }
}
