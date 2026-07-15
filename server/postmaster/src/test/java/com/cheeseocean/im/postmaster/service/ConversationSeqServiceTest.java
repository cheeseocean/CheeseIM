package com.cheeseocean.im.postmaster.service;

import com.cheeseocean.im.common.core.store.sequence.SequenceRange;
import com.cheeseocean.im.common.core.store.sequence.conversation.ConversationSeqAllocator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationSeqServiceTest {

    @Test
    void nextSeqShouldUseConversationSeqAllocator() {
        ConversationSeqAllocator allocator = mock(ConversationSeqAllocator.class);
        when(allocator.next("c1:userA:userB")).thenReturn(1001L);

        ConversationSeqService service = new ConversationSeqService(allocator);

        assertEquals(1001L, service.nextSeq("c1:userA:userB"));
    }

    @Test
    void allocateBatchShouldUseConversationSeqAllocator() {
        ConversationSeqAllocator allocator = mock(ConversationSeqAllocator.class);
        when(allocator.allocate("c1:userA:userB", 3)).thenReturn(new SequenceRange(11L, 13L));

        ConversationSeqService service = new ConversationSeqService(allocator);

        ConversationSeqService.SeqBatch batch = service.allocateBatch("c1:userA:userB", 3);

        assertEquals(11L, batch.range().startInclusive());
        assertEquals(13L, batch.range().endInclusive());
        assertEquals(10L, batch.lastMaxSeq());
    }
}
