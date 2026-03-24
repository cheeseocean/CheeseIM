package com.cheeseocean.im.common.core.store.idempotency.rocksdb;

import com.cheeseocean.im.common.core.store.idempotency.IdempotencyStore;
import com.cheeseocean.im.common.core.store.rocksdb.RocksDbSupport;
import com.cheeseocean.im.common.core.util.ObjectMapperFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

public class RocksDbIdempotencyStore implements IdempotencyStore {

    private final RocksDbSupport support;

    public RocksDbIdempotencyStore(Path dataDirectory, ObjectMapper objectMapper) {
        this.support = new RocksDbSupport(
                Objects.requireNonNull(dataDirectory, "dataDirectory").resolve("idempotency"),
                Objects.requireNonNull(objectMapper, "objectMapper")
        );
    }

    public RocksDbIdempotencyStore(Path dataDirectory) {
        this(dataDirectory, ObjectMapperFactory.createDefaultMapper());
    }

    @Override
    public synchronized boolean putIfAbsent(String key, Duration ttl) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(ttl, "ttl");
        if (support.get(key, String.class) != null) {
            return false;
        }
        support.put(key, "1", ttl);
        return true;
    }
}
