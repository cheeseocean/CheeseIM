package com.cheeseocean.im.common.core.cache;

import com.github.benmanes.caffeine.cache.Cache;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public class MultiLevelCacheService {

    private final Cache<String, Object> l1Cache;
    private final L2CacheAdapter l2CacheAdapter;

    public MultiLevelCacheService(Cache<String, Object> l1Cache, L2CacheAdapter l2CacheAdapter) {
        this.l1Cache = Objects.requireNonNull(l1Cache, "l1Cache");
        this.l2CacheAdapter = Objects.requireNonNull(l2CacheAdapter, "l2CacheAdapter");
    }

    public <T> T getOrLoad(String key, Class<T> type, Duration ttl, Supplier<T> loader) {
        Objects.requireNonNull(loader, "loader");
        T l1Value = cast(l1Cache.getIfPresent(key), type);
        if (l1Value != null) {
            return l1Value;
        }

        T l2Value = l2CacheAdapter.get(key, type);
        if (l2Value != null) {
            l1Cache.put(key, l2Value);
            return l2Value;
        }

        T loaded = loader.get();
        if (loaded != null) {
            l1Cache.put(key, loaded);
            l2CacheAdapter.put(key, loaded, ttl);
        }
        return loaded;
    }

    public <T> Optional<T> peekL1(String key, Class<T> type) {
        return Optional.ofNullable(cast(l1Cache.getIfPresent(key), type));
    }

    public void put(String key, Object value, Duration ttl) {
        if (value == null) {
            evict(key);
            return;
        }
        l1Cache.put(key, value);
        l2CacheAdapter.put(key, value, ttl);
    }

    public void evict(String key) {
        l1Cache.invalidate(key);
        l2CacheAdapter.evict(key);
    }

    private <T> T cast(Object value, Class<T> type) {
        return type.isInstance(value) ? type.cast(value) : null;
    }
}
