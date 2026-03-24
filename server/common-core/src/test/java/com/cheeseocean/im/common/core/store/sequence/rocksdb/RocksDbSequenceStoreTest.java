package com.cheeseocean.im.common.core.store.sequence.rocksdb;

import com.cheeseocean.im.common.core.store.sequence.SequenceRange;
import com.cheeseocean.im.common.core.util.ObjectMapperFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RocksDbSequenceStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldReserveNonOverlappingRangesAndResumeAfterRestart() {
        RocksDbSequenceStore store = new RocksDbSequenceStore(tempDir, ObjectMapperFactory.createDefaultMapper());

        SequenceRange first = store.reserve("c1", 100);
        SequenceRange second = store.reserve("c1", 100);

        assertEquals(1L, first.startInclusive());
        assertEquals(100L, first.endInclusive());
        assertEquals(101L, second.startInclusive());
        assertEquals(200L, second.endInclusive());

        RocksDbSequenceStore restarted = new RocksDbSequenceStore(tempDir, ObjectMapperFactory.createDefaultMapper());
        SequenceRange afterRestart = restarted.reserve("c1", 100);

        assertEquals(201L, afterRestart.startInclusive());
        assertEquals(300L, afterRestart.endInclusive());
    }
}
