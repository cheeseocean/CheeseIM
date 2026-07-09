package com.cheeseocean.im.business.service.conversation;

import com.cheeseocean.im.common.core.business.repository.UserConversationRepository;
import com.cheeseocean.im.common.core.business.repository.UserConversationSyncPointRepository;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.argThat;
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

        org.junit.jupiter.api.Assertions.assertEquals(first, second);
    }
}
