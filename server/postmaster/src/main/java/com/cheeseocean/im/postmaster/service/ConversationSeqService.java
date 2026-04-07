package com.cheeseocean.im.postmaster.service;

import com.cheeseocean.im.common.core.store.sequence.ConversationSequenceAllocator;
import com.cheeseocean.im.common.core.store.sequence.SequenceRange;
import com.cheeseocean.im.common.core.store.sequence.id.SequenceIdGenerator;
import org.springframework.stereotype.Service;

@Service
public class ConversationSeqService {

    private final SequenceIdGenerator sequenceAllocator;

    public ConversationSeqService(SequenceIdGenerator sequenceAllocator) {
        this.sequenceAllocator = sequenceAllocator;
    }

    public long nextSeq(String conversationId) {
        return sequenceAllocator.next(conversationId);
    }

    public SeqBatch allocateBatch(String conversationId, int count) {
        SequenceRange range = sequenceAllocator.allocate(conversationId, count);
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
