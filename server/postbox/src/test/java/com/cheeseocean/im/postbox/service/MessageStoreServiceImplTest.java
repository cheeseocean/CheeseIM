package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.dto.MessageProto;
import com.cheeseocean.im.common.dto.DeliveryAck;
import com.cheeseocean.im.common.dto.DeliveryResult;
import com.cheeseocean.im.common.entity.DeliveryState;
import com.cheeseocean.im.common.entity.InboxMessage;
import com.cheeseocean.im.postbox.entity.InboxDocument;
import com.cheeseocean.im.postbox.entity.MessageDocument;
import com.cheeseocean.im.postbox.repository.InboxDocumentRepository;
import com.cheeseocean.im.postbox.repository.MessageDocumentRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageStoreServiceImplTest {

    @Test
    void saveShouldPersistStoredMessageAndInboxRecord() {
        MessageDocumentRepository messageRepository = mock(MessageDocumentRepository.class);
        InboxDocumentRepository inboxRepository = mock(InboxDocumentRepository.class);
        HistoryTaskPersistenceService persistenceService = mock(HistoryTaskPersistenceService.class);
        HistoryTaskPersistenceService.PersistedHistory persisted = new HistoryTaskPersistenceService.PersistedHistory(
                new MessageDocument(), List.of(7L), true);
        when(persistenceService.persist(any())).thenReturn(persisted);

        MessageStoreServiceImpl service = new MessageStoreServiceImpl(messageRepository, inboxRepository, persistenceService);

        MessageProto message = new MessageProto();
        message.setClientMsgId("c-1");
        message.setServerMsgId("s-1");
        message.setConversationId("single:userA:userB");
        message.setSenderId("userA");
        message.setReceiverId("userB");
        message.setContent("hello");
        message.setContentType(101);
        message.setSequence(7L);

        long inboxSeq = service.saveOfflineMessage(message);

        assertEquals(7L, inboxSeq);
        verify(persistenceService).persist(any());
    }

    @Test
    void getOfflineMessagesShouldReturnUnreadInboxRecordsOrderedBySequence() {
        MessageDocumentRepository messageRepository = mock(MessageDocumentRepository.class);
        InboxDocumentRepository inboxRepository = mock(InboxDocumentRepository.class);
        HistoryTaskPersistenceService persistenceService = mock(HistoryTaskPersistenceService.class);
        when(messageRepository.save(any(MessageDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MessageDocument first = new MessageDocument();
        first.setServerMsgId("s-1");
        first.setConversationId("single:userA:userB");
        first.setSenderId("userA");
        first.setReceiverId("userB");
        first.setContent("one");
        first.setContentType(101);

        MessageDocument second = new MessageDocument();
        second.setServerMsgId("s-2");
        second.setConversationId("single:userA:userB");
        second.setSenderId("userA");
        second.setReceiverId("userB");
        second.setContent("two");
        second.setContentType(101);

        InboxDocument firstInbox = new InboxDocument();
        firstInbox.setUserId("userB");
        firstInbox.setServerMsgId("s-1");
        firstInbox.setConversationId("single:userA:userB");
        firstInbox.setSequence(2L);
        firstInbox.setRead(false);

        InboxDocument secondInbox = new InboxDocument();
        secondInbox.setUserId("userB");
        secondInbox.setServerMsgId("s-2");
        secondInbox.setConversationId("single:userA:userB");
        secondInbox.setSequence(5L);
        secondInbox.setRead(false);

        when(inboxRepository.findByUserIdAndReadIsFalseOrderBySequenceAsc("userB"))
                .thenReturn(List.of(firstInbox, secondInbox));
        when(messageRepository.findByServerMsgId("s-1")).thenReturn(first);
        when(messageRepository.findByServerMsgId("s-2")).thenReturn(second);

        MessageStoreServiceImpl service = new MessageStoreServiceImpl(messageRepository, inboxRepository, persistenceService);

        List<InboxMessage> messages = service.getOfflineMessages("userB", 10);

        assertEquals(2, messages.size());
        assertEquals("s-1", messages.get(0).getServerMsgId());
        assertEquals(2L, messages.get(0).getSequence());
        assertFalse(messages.get(0).isRead());
        assertEquals("s-2", messages.get(1).getServerMsgId());
    }

    @Test
    void laterReadEventShouldWinOverEarlierReceiveAck() {
        MessageDocumentRepository messageRepository = mock(MessageDocumentRepository.class);
        InboxDocumentRepository inboxRepository = mock(InboxDocumentRepository.class);

        InboxDocument inbox = new InboxDocument();
        inbox.setId("userB:s-1");
        inbox.setUserId("userB");
        inbox.setServerMsgId("s-1");
        inbox.setRead(true);
        when(inboxRepository.findById("userB:s-1")).thenReturn(Optional.of(inbox));
        HistoryTaskPersistenceService persistenceService = mock(HistoryTaskPersistenceService.class);

        MessageStoreServiceImpl service = new MessageStoreServiceImpl(messageRepository, inboxRepository, persistenceService);

        DeliveryResult result = service.applyAck(DeliveryAck.receive(
                "s-1", "single:userA:userB", "userB", "ios-1", System.currentTimeMillis()));

        assertEquals(DeliveryState.READ, result.getState());
    }

    @Test
    void groupMessageShouldPersistOneStoredFactAndMultipleInboxTargets() {
        MessageDocumentRepository messageRepository = mock(MessageDocumentRepository.class);
        InboxDocumentRepository inboxRepository = mock(InboxDocumentRepository.class);
        HistoryTaskPersistenceService persistenceService = mock(HistoryTaskPersistenceService.class);
        when(persistenceService.persist(any()))
                .thenReturn(new HistoryTaskPersistenceService.PersistedHistory(new MessageDocument(), List.of(10L, 10L), true));

        MessageStoreServiceImpl service = new MessageStoreServiceImpl(messageRepository, inboxRepository, persistenceService);

        MessageProto message = new MessageProto();
        message.setClientMsgId("c-g-1");
        message.setServerMsgId("s-g-1");
        message.setConversationId("group:g-1");
        message.setSenderId("userA");
        message.setContent("hello group");
        message.setContentType(101);
        message.setSequence(10L);

        List<Long> sequences = service.saveOfflineMessages(message, List.of("userB", "userC"));

        assertIterableEquals(List.of(10L, 10L), sequences);
        verify(persistenceService).persist(any());
    }

    @Test
    void markDeliveredShouldStampDeliveryWithoutMarkingMessageRead() {
        MessageDocumentRepository messageRepository = mock(MessageDocumentRepository.class);
        InboxDocumentRepository inboxRepository = mock(InboxDocumentRepository.class);
        HistoryTaskPersistenceService persistenceService = mock(HistoryTaskPersistenceService.class);

        InboxDocument inbox = new InboxDocument();
        inbox.setId("userB:s-1");
        inbox.setUserId("userB");
        inbox.setServerMsgId("s-1");
        inbox.setRead(false);
        when(inboxRepository.findById("userB:s-1")).thenReturn(Optional.of(inbox));

        MessageStoreServiceImpl service = new MessageStoreServiceImpl(messageRepository, inboxRepository, persistenceService);

        service.markDelivered("userB", "s-1");

        assertFalse(inbox.isRead());
        verify(inboxRepository).save(eq(inbox));
    }

    @Test
    void persistShouldUpsertMessageOnceAndWriteInboxPerReceiver() {
        MessageDocumentRepository messageRepository = mock(MessageDocumentRepository.class);
        InboxDocumentRepository inboxRepository = mock(InboxDocumentRepository.class);
        when(messageRepository.existsById("msg-1")).thenReturn(false, true);
        when(inboxRepository.existsById("userB:msg-1")).thenReturn(false, true);
        when(inboxRepository.existsById("userC:msg-1")).thenReturn(false, true);
        when(messageRepository.save(any(MessageDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(inboxRepository.save(any(InboxDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageRepository.findById("msg-1")).thenReturn(Optional.of(messageDocument("msg-1", 10L)));
        when(inboxRepository.findById("userB:msg-1")).thenReturn(Optional.of(inbox("userB", "msg-1", 10L)));
        when(inboxRepository.findById("userC:msg-1")).thenReturn(Optional.of(inbox("userC", "msg-1", 10L)));

        HistoryTaskPersistenceService service = new HistoryTaskPersistenceService(messageRepository, inboxRepository);

        com.cheeseocean.im.common.dto.HistoryTask task = new com.cheeseocean.im.common.dto.HistoryTask();
        task.setEventId("evt-1");
        task.setMessageId("msg-1");
        task.setClientMsgId("c-1");
        task.setConversationId("group:g-1");
        task.setConversationSeq(10L);
        task.setSenderId("userA");
        task.setContent("hello");
        task.setContentType(101);
        task.setTargetUserIds(List.of("userB", "userC"));

        service.persist(task);
        service.persist(task);

        verify(messageRepository, times(1)).save(any(MessageDocument.class));
        verify(inboxRepository, times(2)).save(any(InboxDocument.class));
    }

    private static MessageDocument messageDocument(String serverMsgId, Long sequence) {
        MessageDocument document = new MessageDocument();
        document.setServerMsgId(serverMsgId);
        document.setSequence(sequence);
        return document;
    }

    private static InboxDocument inbox(String userId, String serverMsgId, Long sequence) {
        InboxDocument inbox = new InboxDocument();
        inbox.setId(userId + ":" + serverMsgId);
        inbox.setUserId(userId);
        inbox.setServerMsgId(serverMsgId);
        inbox.setSequence(sequence);
        return inbox;
    }
}
