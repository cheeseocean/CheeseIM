package com.cheeseocean.im.common.core.cache;

import java.time.Duration;

public interface L2CacheAdapter {

    <T> T get(String key, Class<T> type);

    void put(String key, Object value, Duration ttl);

    void evict(String key);
}
