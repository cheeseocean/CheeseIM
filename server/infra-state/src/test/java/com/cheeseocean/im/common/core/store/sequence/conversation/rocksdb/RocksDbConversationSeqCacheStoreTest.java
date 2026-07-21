package com.cheeseocean.im.common.core.store.sequence.conversation.rocksdb;

import com.cheeseocean.im.common.core.store.sequence.conversation.ConversationSeqCacheResult;
import com.cheeseocean.im.common.core.store.sequence.conversation.ConversationSeqRangeState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RocksDbConversationSeqCacheStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void allocateShouldInitializeAndServeCachedRange() {
        RocksDbConversationSeqCacheStore store = new RocksDbConversationSeqCacheStore(tempDir);

        ConversationSeqCacheResult miss = store.allocate("s:u100:u200", 2, 100L);
        assertEquals(ConversationSeqRangeState.MISS, miss.state());
        assertTrue(miss.ownerToken() != null && !miss.ownerToken().isBlank());

        store.install("s:u100:u200", miss.ownerToken(), 2L, 52L, 100L);

        ConversationSeqCacheResult hit = store.allocate("s:u100:u200", 2, 101L);
        assertEquals(ConversationSeqRangeState.ALLOCATED, hit.state());
        assertEquals(2L, hit.currentSeq());
        assertEquals(52L, hit.lastSeq());
        assertEquals(4L, store.getCachedMaxSeq("s:u100:u200"));
    }

    @Test
    void allocateShouldReturnExhaustedWhenCachedRangeRunsOut() {
        RocksDbConversationSeqCacheStore store = new RocksDbConversationSeqCacheStore(tempDir);

        ConversationSeqCacheResult miss = store.allocate("g:g100", 3, 100L);
        store.install("g:g100", miss.ownerToken(), 3L, 3L, 100L);

        ConversationSeqCacheResult exhausted = store.allocate("g:g100", 1, 101L);
        assertEquals(ConversationSeqRangeState.EXHAUSTED, exhausted.state());
        assertEquals(3L, exhausted.currentSeq());
        assertEquals(3L, exhausted.lastSeq());
        assertTrue(exhausted.ownerToken() != null && !exhausted.ownerToken().isBlank());
    }
}
