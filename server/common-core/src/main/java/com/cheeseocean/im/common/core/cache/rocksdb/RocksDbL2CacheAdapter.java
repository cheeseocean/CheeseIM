package com.cheeseocean.im.common.core.cache.rocksdb;

import com.cheeseocean.im.common.core.cache.L2CacheAdapter;
import com.cheeseocean.im.common.core.store.rocksdb.RocksDbSupport;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

public class RocksDbL2CacheAdapter implements L2CacheAdapter {

    private final RocksDbSupport support;

    public RocksDbL2CacheAdapter(Path dataDirectory, ObjectMapper objectMapper) {
        this.support = new RocksDbSupport(
                Objects.requireNonNull(dataDirectory, "dataDirectory"),
                Objects.requireNonNull(objectMapper, "objectMapper")
        );
    }

    @Override
    public <T> T get(String key, Class<T> type) {
        return support.get(key, type);
    }

    @Override
    public void put(String key, Object value, Duration ttl) {
        support.put(key, value, ttl);
    }

    @Override
    public void evict(String key) {
        support.delete(key);
    }
}
