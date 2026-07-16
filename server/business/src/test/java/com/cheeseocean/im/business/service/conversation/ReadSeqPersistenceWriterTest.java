package com.cheeseocean.im.business.service.conversation;

import com.cheeseocean.im.common.core.business.repository.UserConversationRepository;
import com.cheeseocean.im.common.core.business.repository.UserConversationSyncPointRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ReadSeqPersistenceWriterTest {

    @Test
    void shutdownShouldPersistAggregatedMaxReadSeqFromAllBuckets() {
        UserConversationSyncPointRepository offsetRepository = mock(UserConversationSyncPointRepository.class);
        UserConversationRepository stateRepository = mock(UserConversationRepository.class);
        ReadSeqPersistenceWriter writer = new ReadSeqPersistenceWriter(
                offsetRepository,
                stateRepository,
                2,
                16,
                false);

        writer.enqueue("u1", "s:u1:u2", 3L);
        writer.enqueue("u1", "s:u1:u2", 7L);
        writer.enqueue("u1", "s:u1:u2", 5L);
        writer.enqueue("u2", "s:u2:u3", 9L);
        writer.shutdown();

        verify(offsetRepository).updateReadSeq("u1", "s:u1:u2", 7L);
        verify(offsetRepository).updateReadSeq("u2", "s:u2:u3", 9L);
        verify(stateRepository).updateFields(eq("u1"), eq("s:u1:u2"),
                argThat(fields -> Integer.valueOf(0).equals(fields.get("unreadCount"))));
        verify(stateRepository).updateFields(eq("u2"), eq("s:u2:u3"),
                argThat(fields -> Integer.valueOf(0).equals(fields.get("unreadCount"))));
    }

    @Test
    void bucketIndexShouldRouteSameUserToSameWorker() {
        ReadSeqPersistenceWriter writer = new ReadSeqPersistenceWriter(
                mock(UserConversationSyncPointRepository.class),
                mock(UserConversationRepository.class),
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
                .when(repository).updateReadSeq("u1", "c3", 3L);
        ReadSeqPersistenceWriter writer = new ReadSeqPersistenceWriter(
                repository,
                mock(UserConversationRepository.class),
                1,
                1,
                false);

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
        UserConversationSyncPointRepository offsetRepository = mock(UserConversationSyncPointRepository.class);
        doThrow(new IllegalStateException("mongo unavailable"))
                .when(offsetRepository).updateReadSeq(anyString(), anyString(), anyLong());
        ReadSeqPersistenceWriter writer = new ReadSeqPersistenceWriter(
                offsetRepository, mock(UserConversationRepository.class), 1, 8, true);

        writer.enqueue("u1", "c1", 9L);

        verify(offsetRepository, org.mockito.Mockito.timeout(3000).atLeast(4))
                .updateReadSeq("u1", "c1", 9L);
        org.junit.jupiter.api.Assertions.assertTrue(writer.stats().retryScheduled() >= 3L);
        assertEquals(0L, writer.stats().exhaustedFailures());
        writer.shutdown();
    }
}
