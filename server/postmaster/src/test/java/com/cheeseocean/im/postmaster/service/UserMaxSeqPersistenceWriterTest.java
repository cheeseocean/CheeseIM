package com.cheeseocean.im.postmaster.service;

import com.cheeseocean.im.common.core.business.repository.UserConversationSyncPointRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
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

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UserConversationSyncPointRepository.MaxSeqUpdate>> updates =
                ArgumentCaptor.forClass(List.class);
        verify(syncPointRepository).updateMaxSeqBatch(updates.capture());
        org.assertj.core.api.Assertions.assertThat(updates.getValue()).containsExactlyInAnyOrder(
                new UserConversationSyncPointRepository.MaxSeqUpdate("u1", "s:u1:u2", 12L),
                new UserConversationSyncPointRepository.MaxSeqUpdate("u2", "s:u2:u3", 8L));
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

    @Test
    void mongoLongFailureShouldKeepCapacityBoundedAndFailExplicitlyWhenBothQueuesAreFull() {
        UserConversationSyncPointRepository repository = mock(UserConversationSyncPointRepository.class);
        doThrow(new IllegalStateException("mongo unavailable"))
                .when(repository).updateMaxSeqBatch(anyList());
        UserMaxSeqPersistenceWriter writer = new UserMaxSeqPersistenceWriter(
                repository, 1, 1, false);

        writer.enqueue("u1", "c1", 1L);
        writer.enqueue("u1", "c2", 2L);
        assertThrows(IllegalStateException.class, () -> writer.enqueue("u1", "c3", 3L));

        assertEquals(2L, writer.stats().accepted());
        assertEquals(1L, writer.stats().overflowFallbacks());
        assertEquals(1L, writer.stats().exhaustedFailures());
        writer.shutdown();
    }

    @Test
    void mongoFailureShouldKeepRetryingInsteadOfPermanentlyDropping() {
        UserConversationSyncPointRepository repository = mock(UserConversationSyncPointRepository.class);
        doThrow(new IllegalStateException("mongo unavailable"))
                .when(repository).updateMaxSeqBatch(anyList());
        UserMaxSeqPersistenceWriter writer = new UserMaxSeqPersistenceWriter(repository, 1, 8, true);

        writer.enqueue("u1", "c1", 9L);

        verify(repository, org.mockito.Mockito.timeout(3000).atLeast(4))
                .updateMaxSeqBatch(anyList());
        org.junit.jupiter.api.Assertions.assertTrue(writer.stats().retryScheduled() >= 3L);
        assertEquals(0L, writer.stats().exhaustedFailures());
        writer.shutdown();
    }
}
