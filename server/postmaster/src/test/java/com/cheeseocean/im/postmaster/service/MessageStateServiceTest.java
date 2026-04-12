package com.cheeseocean.im.postmaster.service;

import com.cheeseocean.im.common.api.dto.message.ConversationLastMessageSummary;
import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.dto.message.MessageOptions;
import com.cheeseocean.im.common.api.enums.ContentType;
import com.cheeseocean.im.common.api.enums.MessagePreviewType;
import com.cheeseocean.im.common.api.enums.SessionType;
import com.cheeseocean.im.common.core.store.conversation.ConversationStateStore;
import com.cheeseocean.im.postmaster.model.MessageWithTargets;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class MessageStateServiceTest {

    private static MessageStateService newService(ConversationStateStore store) {
        return new MessageStateService(store, new ObjectMapper(), mock(UserMaxSeqPersistenceWriter.class));
    }

    @Test
    void applyShouldPersistTypedConversationLastMessageSummary() throws Exception {
        ConversationStateStore conversationStateStore = mock(ConversationStateStore.class);
        MessageStateService service = newService(conversationStateStore);

        service.apply(systemNotificationMessage(9L), List.of("userB"));

        var valueCaptor = forClass(String.class);
        verify(conversationStateStore).setLastMessageSummary(eq("s:userA:userB"), valueCaptor.capture());
        ConversationLastMessageSummary summary =
                new ObjectMapper().readValue(valueCaptor.getValue(), ConversationLastMessageSummary.class);

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
        MessageStateService service = newService(conversationStateStore);

        service.apply(systemNotificationMessage(9L), List.of("userB"));

        verify(conversationStateStore).incrementUnread("userB", "s:userA:userB");
    }

    @Test
    void processReadReceiptsShouldWriteReadSeqToRedis() {
        ConversationStateStore store = mock(ConversationStateStore.class);
        MessageStateService service = newService(store);

        service.processReadReceipts(List.of(readReceiptMessage(
                "userA", "userB", "{\"conversationId\":\"s:userA:userB\",\"seq\":42}".getBytes(StandardCharsets.UTF_8))));

        verify(store).setUserReadSeq("userA", "s:userA:userB", 42L);
    }

    @Test
    void processReadReceiptsShouldAggregateToMaxSeqPerUserConversation() {
        ConversationStateStore store = mock(ConversationStateStore.class);
        MessageStateService service = newService(store);

        service.processReadReceipts(List.of(
                readReceiptMessage("userA", "userB", "{\"conversationId\":\"s:userA:userB\",\"seq\":10}".getBytes(StandardCharsets.UTF_8)),
                readReceiptMessage("userA", "userB", "{\"conversationId\":\"s:userA:userB\",\"seq\":20}".getBytes(StandardCharsets.UTF_8))
        ));

        verify(store).setUserReadSeq("userA", "s:userA:userB", 20L);
        verify(store, never()).setUserReadSeq("userA", "s:userA:userB", 10L);
    }

    @Test
    void applyBatchShouldAggregateUnreadAndReadSeq() {
        ConversationStateStore store = mock(ConversationStateStore.class);
        MessageStateService service = newService(store);

        Message first = directTextMessage("userA", "userB", 5L, "hello");
        Message second = directTextMessage("userA", "userB", 6L, "world");

        service.applyBatch(List.of(
                new MessageWithTargets(first, List.of("userB")),
                new MessageWithTargets(second, List.of("userB"))
        ));

        verify(store).setConversationMinSeqIfAbsent("s:userA:userB", 5L);
        verify(store).setConversationMaxSeq("s:userA:userB", 6L);
        verify(store).setUserMaxSeq("userA", "s:userA:userB", 6L);
        verify(store).setUserMaxSeq("userB", "s:userA:userB", 6L);
        verify(store).setUserReadSeq("userA", "s:userA:userB", 6L);
        verify(store).incrementUnreadBy("userB", "s:userA:userB", 2);
    }

    private static Message systemNotificationMessage(long seq) {
        MessageOptions options = new MessageOptions();
        options.setNeedConversation(true);
        options.setNeedUnreadCount(true);
        options.setNeedLastMessage(true);
        options.setNotification(true);

        Message message = new Message();
        message.setSeq(seq);
        message.setSenderId("userA");
        message.setReceiverId("userB");
        message.setSessionType(SessionType.SINGLE);
        message.setContentType(ContentType.SYSTEM_NOTIFY);
        message.setContent("hello".getBytes(StandardCharsets.UTF_8));
        message.setSendTime(123L);
        message.setOptions(options);
        return message;
    }

    private static Message directTextMessage(String senderId, String receiverId, long seq, String content) {
        MessageOptions options = new MessageOptions();
        options.setNeedConversation(true);
        options.setNeedUnreadCount(true);
        options.setNeedLastMessage(true);

        Message message = new Message();
        message.setSeq(seq);
        message.setSenderId(senderId);
        message.setReceiverId(receiverId);
        message.setSessionType(SessionType.SINGLE);
        message.setContentType(ContentType.TEXT);
        message.setContent(content.getBytes(StandardCharsets.UTF_8));
        message.setSendTime(123L + seq);
        message.setOptions(options);
        return message;
    }

    private static Message readReceiptMessage(String senderId, String receiverId, byte[] content) {
        Message message = new Message();
        message.setSenderId(senderId);
        message.setReceiverId(receiverId);
        message.setSessionType(SessionType.SINGLE);
        message.setContentType(ContentType.READ_RECEIPT);
        message.setContent(content);
        return message;
    }
}
