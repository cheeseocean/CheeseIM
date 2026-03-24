package com.cheeseocean.im.common.core.store.idempotency;

import java.time.Duration;

public interface IdempotencyStore {

    boolean putIfAbsent(String key, Duration ttl);
}
