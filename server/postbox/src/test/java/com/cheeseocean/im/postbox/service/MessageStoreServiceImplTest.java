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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageStoreServiceImplTest {

    @Test
    void saveShouldPersistStoredMessageAndInboxRecord() {
        MessageDocumentRepository messageRepository = mock(MessageDocumentRepository.class);
        InboxDocumentRepository inboxRepository = mock(InboxDocumentRepository.class);
        when(messageRepository.save(any(MessageDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(inboxRepository.save(any(InboxDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MessageStoreServiceImpl service = new MessageStoreServiceImpl(messageRepository, inboxRepository);

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
        verify(messageRepository).save(any(MessageDocument.class));
        verify(inboxRepository).save(any(InboxDocument.class));
    }

    @Test
    void getOfflineMessagesShouldReturnUnreadInboxRecordsOrderedBySequence() {
        MessageDocumentRepository messageRepository = mock(MessageDocumentRepository.class);
        InboxDocumentRepository inboxRepository = mock(InboxDocumentRepository.class);
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

        MessageStoreServiceImpl service = new MessageStoreServiceImpl(messageRepository, inboxRepository);

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

        MessageStoreServiceImpl service = new MessageStoreServiceImpl(messageRepository, inboxRepository);

        DeliveryResult result = service.applyAck(DeliveryAck.receive(
                "s-1", "single:userA:userB", "userB", "ios-1", System.currentTimeMillis()));

        assertEquals(DeliveryState.READ, result.getState());
    }

    @Test
    void groupMessageShouldPersistOneStoredFactAndMultipleInboxTargets() {
        MessageDocumentRepository messageRepository = mock(MessageDocumentRepository.class);
        InboxDocumentRepository inboxRepository = mock(InboxDocumentRepository.class);
        when(messageRepository.save(any(MessageDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(inboxRepository.save(any(InboxDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MessageStoreServiceImpl service = new MessageStoreServiceImpl(messageRepository, inboxRepository);

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
        verify(messageRepository).save(any(MessageDocument.class));
        verify(inboxRepository, times(2)).save(any(InboxDocument.class));
    }
}
