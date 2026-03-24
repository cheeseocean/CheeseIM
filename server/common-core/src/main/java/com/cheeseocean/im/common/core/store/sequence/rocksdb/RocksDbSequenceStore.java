package com.cheeseocean.im.common.core.store.sequence.rocksdb;

import com.cheeseocean.im.common.core.store.rocksdb.RocksDbSupport;
import com.cheeseocean.im.common.core.store.sequence.SequenceRange;
import com.cheeseocean.im.common.core.store.sequence.SequenceStore;
import com.cheeseocean.im.common.core.util.ObjectMapperFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.Objects;

public class RocksDbSequenceStore implements SequenceStore {

    private final RocksDbSupport support;

    public RocksDbSequenceStore(Path dataDirectory, ObjectMapper objectMapper) {
        this.support = new RocksDbSupport(
                Objects.requireNonNull(dataDirectory, "dataDirectory").resolve("sequence"),
                Objects.requireNonNull(objectMapper, "objectMapper")
        );
    }

    public RocksDbSequenceStore(Path dataDirectory) {
        this(dataDirectory, ObjectMapperFactory.createDefaultMapper());
    }

    @Override
    public synchronized SequenceRange reserve(String conversationId, int size) {
        Objects.requireNonNull(conversationId, "conversationId");
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }

        Long storedHighWatermark = support.get(conversationId, Long.class);
        long previousHighWatermark = storedHighWatermark == null ? 0L : storedHighWatermark;
        long startInclusive = Math.addExact(previousHighWatermark, 1L);
        long endInclusive = Math.addExact(startInclusive, size - 1L);
        support.put(conversationId, endInclusive, null);
        return new SequenceRange(startInclusive, endInclusive);
    }
}
