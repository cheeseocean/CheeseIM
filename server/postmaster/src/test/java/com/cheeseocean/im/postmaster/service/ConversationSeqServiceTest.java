package com.cheeseocean.im.postmaster.service;

import com.cheeseocean.im.common.core.store.sequence.ConversationSequenceAllocator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationSeqServiceTest {

    @Test
    void nextSeqShouldUseConversationSequenceAllocator() {
        ConversationSequenceAllocator allocator = mock(ConversationSequenceAllocator.class);
        when(allocator.nextSeq("c1:userA:userB")).thenReturn(1001L);

        ConversationSeqService service = new ConversationSeqService(allocator);

        assertEquals(1001L, service.nextSeq("c1:userA:userB"));
    }
}
