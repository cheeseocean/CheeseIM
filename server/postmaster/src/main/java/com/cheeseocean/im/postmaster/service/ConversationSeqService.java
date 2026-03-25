package com.cheeseocean.im.postmaster.service;

import com.cheeseocean.im.common.core.store.sequence.ConversationSequenceAllocator;
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
}
