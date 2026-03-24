package com.cheeseocean.im.postman.service;

import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.common.core.store.idempotency.IdempotencyStore;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class ConsumerDedupService {

    private static final Duration TTL = Duration.ofHours(24);

    private final IdempotencyStore idempotencyStore;

    public ConsumerDedupService(IdempotencyStore idempotencyStore) {
        this.idempotencyStore = idempotencyStore;
    }

    public boolean markIfAbsent(String consumerGroup, String eventId) {
        return idempotencyStore.putIfAbsent(RedisKeys.consumerDedup(consumerGroup, eventId), TTL);
    }
}
