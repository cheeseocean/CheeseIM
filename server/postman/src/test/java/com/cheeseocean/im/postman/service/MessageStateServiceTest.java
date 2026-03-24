package com.cheeseocean.im.postman.service;

import com.cheeseocean.im.common.api.dto.message.ConversationLastMessageSummary;
import com.cheeseocean.im.common.api.dto.message.MessageOptions;
import com.cheeseocean.im.common.api.dto.message.SequencedMessage;
import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.common.core.enums.ContentType;
import com.cheeseocean.im.common.core.enums.MessagePreviewType;
import com.cheeseocean.im.common.core.store.conversation.ConversationStateStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MessageStateServiceTest {

    @Test
    void applyShouldPersistTypedConversationLastMessageSummary() throws Exception {
        ConversationStateStore conversationStateStore = mock(ConversationStateStore.class);

        MessageStateService service = new MessageStateService(conversationStateStore, new ObjectMapper());
        SequencedMessage message = message();

        service.apply(message, List.of("userB"));

        var valueCaptor = forClass(String.class);
        verify(conversationStateStore).setLastMessageSummary(eq("c1:userA:userB"), valueCaptor.capture());
        String summaryJson = valueCaptor.getValue();
        ConversationLastMessageSummary summary =
                new ObjectMapper().readValue(summaryJson, ConversationLastMessageSummary.class);

        assertEquals(9L, summary.getSeq());
        assertEquals("userA", summary.getSenderId());
        assertEquals("hello", summary.getContent());
        assertEquals(ContentType.SYSTEM_NOTIFY.getCode(), summary.getContentType());
        assertEquals("系统通知", summary.getPreviewText());
        assertEquals(MessagePreviewType.SYSTEM, summary.getPreviewType());
        assertTrue(summary.isNotification());
    }

    @Test
    void applyShouldIncrementUnreadForRecipientsOnly() {
        ConversationStateStore conversationStateStore = mock(ConversationStateStore.class);

        MessageStateService service = new MessageStateService(conversationStateStore, new ObjectMapper());

        service.apply(message(), List.of("userB"));

        verify(conversationStateStore).incrementUnread("userB", "c1:userA:userB");
    }

    @Test
    void applyShouldRejectReadReceiptMessages() {
        ConversationStateStore conversationStateStore = mock(ConversationStateStore.class);

        MessageStateService service = new MessageStateService(conversationStateStore, new ObjectMapper());
        SequencedMessage message = message();
        message.setContentType(ContentType.READ_RECEIPT.getCode());

        assertThrows(IllegalStateException.class, () -> service.apply(message, List.of("userB")));
    }

    private SequencedMessage message() {
        MessageOptions options = new MessageOptions();
        options.setNeedConversation(true);
        options.setNeedUnreadCount(true);
        options.setNeedLastMessage(true);
        options.setNotification(true);

        SequencedMessage message = new SequencedMessage();
        message.setConversationId("c1:userA:userB");
        message.setSeq(9L);
        message.setSenderId("userA");
        message.setRecvId("userB");
        message.setContentType(ContentType.SYSTEM_NOTIFY.getCode());
        message.setContent("hello");
        message.setSendTime(123L);
        message.setOptions(options);
        return message;
    }
}
