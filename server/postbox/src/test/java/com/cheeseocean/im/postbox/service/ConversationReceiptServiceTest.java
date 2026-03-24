package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.core.store.conversation.ConversationStateStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ConversationReceiptServiceTest {

    @Test
    void applyReadCursorShouldWriteUserReadSeq() {
        ConversationStateStore conversationStateStore = mock(ConversationStateStore.class);

        ConversationReceiptService service = new ConversationReceiptService(conversationStateStore);

        service.applyReadCursor("userB", "c1:userA:userB", 19L);

        verify(conversationStateStore).setUserReadSeq("userB", "c1:userA:userB", 19L);
    }

    @Test
    void applyReadCursorShouldRejectMissingSeq() {
        ConversationStateStore conversationStateStore = mock(ConversationStateStore.class);
        ConversationReceiptService service = new ConversationReceiptService(conversationStateStore);

        assertThrows(IllegalArgumentException.class,
                () -> service.applyReadCursor("userB", "c1:userA:userB", null));
    }
}
