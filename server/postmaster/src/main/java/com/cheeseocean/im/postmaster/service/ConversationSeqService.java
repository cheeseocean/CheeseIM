package com.cheeseocean.im.postmaster.service;

import com.cheeseocean.im.common.core.store.sequence.ConversationSequenceAllocator;
import com.cheeseocean.im.common.core.store.sequence.SequenceRange;
import org.springframework.stereotype.Service;

@Service
public class ConversationSeqService {

    private final ConversationSequenceAllocator sequenceAllocator;

    public ConversationSeqService(ConversationSequenceAllocator sequenceAllocator) {
        this.sequenceAllocator = sequenceAllocator;
    }

    public long nextSeq(String conversationId) {
        return sequenceAllocator.nextSeq(conversationId);
    }

    public SeqBatch allocateBatch(String conversationId, int count) {
        SequenceRange range = sequenceAllocator.allocateRange(conversationId, count);
        return new SeqBatch(range);
    }

    public record SeqBatch(SequenceRange range) {
        public long lastMaxSeq() {
            return range.startInclusive() - 1;
        }
        public boolean isNewConversation() {
            return range.startInclusive() == 1L;
        }
    }
}
