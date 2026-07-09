package com.cheeseocean.im.postmaster.service;

import com.cheeseocean.im.common.core.business.repository.UserConversationSyncPointRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class UserMaxSeqPersistenceWriterTest {

    @Test
    void shutdownShouldPersistAggregatedMaxSeqFromAllBuckets() {
        UserConversationSyncPointRepository syncPointRepository = mock(UserConversationSyncPointRepository.class);
        UserMaxSeqPersistenceWriter writer = new UserMaxSeqPersistenceWriter(syncPointRepository, 2, 16, false);

        writer.enqueue("u1", "s:u1:u2", 10L);
        writer.enqueue("u1", "s:u1:u2", 12L);
        writer.enqueue("u1", "s:u1:u2", 11L);
        writer.enqueue("u2", "s:u2:u3", 8L);
        writer.shutdown();

        verify(syncPointRepository).updateMaxSeq("u1", "s:u1:u2", 12L);
        verify(syncPointRepository).updateMaxSeq("u2", "s:u2:u3", 8L);
    }

    @Test
    void bucketIndexShouldRouteSameUserToSameWorker() {
        UserMaxSeqPersistenceWriter writer = new UserMaxSeqPersistenceWriter(
                mock(UserConversationSyncPointRepository.class),
                4,
                16,
                false);

        int first = writer.bucketIndex("u100");
        int second = writer.bucketIndex("u100");

        writer.shutdown();

        assertEquals(first, second);
    }
}
