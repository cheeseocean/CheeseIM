package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.core.store.conversation.ConversationStateStore;
import org.springframework.stereotype.Service;

@Service
public class ConversationReceiptService {

    private final ConversationStateStore conversationStateStore;

    public ConversationReceiptService(ConversationStateStore conversationStateStore) {
        this.conversationStateStore = conversationStateStore;
    }

    public void applyReadCursor(String userId, String conversationId, Long seq) {
        if (userId == null || userId.isBlank() || conversationId == null || conversationId.isBlank() || seq == null) {
            throw new IllegalArgumentException("read cursor requires userId, conversationId, and seq");
        }
        conversationStateStore.setUserReadSeq(userId, conversationId, seq);
    }
}
