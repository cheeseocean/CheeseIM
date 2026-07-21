package com.cheeseocean.im.common.core.store.idempotency.rocksdb;

import com.cheeseocean.im.common.core.util.ObjectMapperFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RocksDbIdempotencyStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldRejectDuplicateMarkerUntilTheMarkerExpires() throws Exception {
        RocksDbIdempotencyStore store = new RocksDbIdempotencyStore(tempDir, ObjectMapperFactory.createDefaultMapper());

        assertTrue(store.putIfAbsent("dup:1", Duration.ofMillis(50)));
        assertFalse(store.putIfAbsent("dup:1", Duration.ofMillis(50)));

        Thread.sleep(100);

        assertTrue(store.putIfAbsent("dup:1", Duration.ofMillis(50)));
    }
}
