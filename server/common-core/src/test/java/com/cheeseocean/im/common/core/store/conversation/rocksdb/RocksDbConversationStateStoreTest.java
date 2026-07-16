package com.cheeseocean.im.common.core.store.conversation.rocksdb;

import com.cheeseocean.im.common.core.store.conversation.ConversationStateStore;
import com.cheeseocean.im.common.core.util.ObjectMapperFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RocksDbConversationStateStoreTest {

    @TempDir
    Path dataDirectory;

    @Test
    void advanceReadStateShouldNeverMoveCursorBackwards() {
        RocksDbConversationStateStore store = store();
        store.setUserMaxSeq("u1", "g:crew", 10L);
        ConversationStateStore.ReadState first = store.advanceReadState("u1", "g:crew", 8L, 0L, 10L);
        ConversationStateStore.ReadState stale = store.advanceReadState("u1", "g:crew", 5L, 0L, 10L);

        assertTrue(first.changed());
        assertEquals(8L, stale.readSeq());
        assertEquals(2, stale.unread());
        assertFalse(stale.changed());
    }

    @Test
    void advanceReadStateAndIncrementUnreadShouldRemainConsistentUnderConcurrency() throws Exception {
        RocksDbConversationStateStore store = store();
        store.setUserMaxSeq("u1", "g:crew", 10L);
        store.setUnread("u1", "g:crew", 10);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        executor.submit(() -> { await(start); store.advanceReadState("u1", "g:crew", 10L, 0L, 10L); });
        executor.submit(() -> {
            await(start);
            store.setUserMaxSeq("u1", "g:crew", 11L);
            store.incrementUnread("u1", "g:crew");
        });
        start.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        assertEquals(10L, store.getUserReadSeq("u1", "g:crew"));
        assertEquals(1, store.getUnread("u1", "g:crew"));
    }

    private RocksDbConversationStateStore store() {
        return new RocksDbConversationStateStore(dataDirectory, ObjectMapperFactory.createDefaultMapper());
    }

    private static void await(CountDownLatch latch) {
        try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
