package com.cheeseocean.im.postmaster.service;

import com.cheeseocean.im.common.api.dto.message.ConversationLastMessageSummary;
import com.cheeseocean.im.common.api.dto.message.MessageOptions;
import com.cheeseocean.im.common.api.dto.message.SequencedMessage;
import com.cheeseocean.im.common.api.event.IngressEvent;
import com.cheeseocean.im.common.core.enums.ContentType;
import com.cheeseocean.im.common.core.enums.MessagePreviewType;
import com.cheeseocean.im.common.core.store.conversation.ConversationStateStore;
import com.cheeseocean.im.postmaster.service.ConversationSyncFacade;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class MessageStateServiceTest {

    @Test
    void applyShouldPersistTypedConversationLastMessageSummary() throws Exception {
        ConversationStateStore conversationStateStore = mock(ConversationStateStore.class);

        MessageStateService service = new MessageStateService(conversationStateStore, new ObjectMapper(), null);
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

        MessageStateService service = new MessageStateService(conversationStateStore, new ObjectMapper(), null);

        service.apply(message(), List.of("userB"));

        verify(conversationStateStore).incrementUnread("userB", "c1:userA:userB");
    }

    @Test
    void processReadReceiptsShouldWriteReadSeqToRedisAndEnqueuePersistence() {
        ConversationStateStore store  = mock(ConversationStateStore.class);
        ConversationSyncFacade syncService = mock(ConversationSyncFacade.class);
        MessageStateService service = new MessageStateService(store, new ObjectMapper(), syncService);

        service.processReadReceipts(List.of(readReceiptEvent("userA", "c1:userA:userB",
                "{\"receiptType\":\"READ_CURSOR\",\"seq\":42}")));

        verify(store).setUserReadSeq("userA", "c1:userA:userB", 42L);
        verify(syncService).markRead("userA", "c1:userA:userB", 42L);
    }

    @Test
    void processReadReceiptsShouldAggregateToMaxSeqPerUserConversation() {
        ConversationStateStore store  = mock(ConversationStateStore.class);
        ConversationSyncFacade syncService = mock(ConversationSyncFacade.class);
        MessageStateService service = new MessageStateService(store, new ObjectMapper(), syncService);

        // Two receipts for same (user, conv) — only max seq should be written
        service.processReadReceipts(List.of(
                readReceiptEvent("userA", "c1:userA:userB", "{\"receiptType\":\"READ_CURSOR\",\"seq\":10}"),
                readReceiptEvent("userA", "c1:userA:userB", "{\"receiptType\":\"READ_CURSOR\",\"seq\":20}")));

        verify(store).setUserReadSeq("userA", "c1:userA:userB", 20L);
        verify(store, never()).setUserReadSeq("userA", "c1:userA:userB", 10L);
        verify(syncService).markRead("userA", "c1:userA:userB", 20L);
    }

    private static IngressEvent readReceiptEvent(String senderId, String conversationId, String content) {
        IngressEvent event = new IngressEvent();
        event.setSenderId(senderId);
        event.setConversationId(conversationId);
        event.setContentType(ContentType.READ_RECEIPT.getCode());
        event.setContent(content);
        return event;
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
