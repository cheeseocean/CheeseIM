package com.cheeseocean.im.common.core.cache.rocksdb;

import com.cheeseocean.im.common.core.util.ObjectMapperFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RocksDbL2CacheAdapterTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldReturnNullAfterTtlExpires() throws Exception {
        RocksDbL2CacheAdapter adapter = new RocksDbL2CacheAdapter(tempDir, ObjectMapperFactory.createDefaultMapper());
        adapter.put("k1", new DemoValue("v1"), Duration.ofMillis(50));
        Thread.sleep(100);

        assertThat(adapter.get("k1", DemoValue.class)).isNull();
    }

    record DemoValue(String value) {
    }
}
