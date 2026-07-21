package com.cheeseocean.im.common.core.cache;

import com.fasterxml.jackson.databind.JavaType;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** StringRedisTemplate 支撑的类型化缓存区域。 */
final class RedisCacheRegion<T> implements CacheRegion<T> {

    private final RedisCacheStore store;
    private final String keyPrefix;
    private final JavaType valueType;
    private final Duration ttl;

    RedisCacheRegion(RedisCacheStore store, String keyPrefix, JavaType valueType, Duration ttl) {
        this.store = Objects.requireNonNull(store, "store");
        this.keyPrefix = Objects.requireNonNull(keyPrefix, "keyPrefix");
        this.valueType = Objects.requireNonNull(valueType, "valueType");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
    }

    @Override
    public T get(String key) {
        return store.get(fullKey(key), valueType);
    }

    @Override
    public Map<String, T> getAll(Collection<String> keys) {
        return store.getAll(keys, this::fullKey, valueType);
    }

    @Override
    public T getOrLoad(String key, Supplier<T> loader) {
        Objects.requireNonNull(loader, "loader");
        T cached = get(key);
        if (cached != null) {
            return cached;
        }
        T loaded = loader.get();
        if (loaded != null) {
            put(key, loaded);
        }
        return loaded;
    }

    @Override
    public void put(String key, T value) {
        put(key, value, ttl);
    }

    @Override
    public void put(String key, T value, Duration valueTtl) {
        if (value == null) {
            evict(key);
            return;
        }
        store.put(fullKey(key), value, Objects.requireNonNull(valueTtl, "valueTtl"));
    }

    @Override
    public void putAll(Map<String, T> values) {
        store.putAll(values, this::fullKey, ttl);
    }

    @Override
    public void evict(String key) {
        store.evict(fullKey(key));
    }

    @Override
    public void evictAll(Collection<String> keys) {
        store.evictAll(keys, this::fullKey);
    }

    private String fullKey(String key) {
        return keyPrefix + Objects.requireNonNull(key, "key");
    }
}
