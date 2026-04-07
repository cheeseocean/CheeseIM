package com.cheeseocean.im.postmaster.listener;

import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.dto.message.MessageOptions;
import com.cheeseocean.im.common.api.enums.ContentType;
import com.cheeseocean.im.common.api.enums.SessionType;
import com.cheeseocean.im.common.api.event.HistoryEvent;
import com.cheeseocean.im.common.api.protocol.ProtoHistoryEventMapper;
import com.cheeseocean.im.common.api.protocol.ProtoMessageMapper;
import com.cheeseocean.im.common.core.constants.TopicNames;
import com.cheeseocean.im.common.core.queue.QueueAdapter;
import com.cheeseocean.im.common.core.store.sequence.SequenceRange;
import com.cheeseocean.im.postmaster.sender.HistoryEventProducer;
import com.cheeseocean.im.postmaster.sender.MessageProducer;
import com.cheeseocean.im.postmaster.service.ConversationSeqService;
import com.cheeseocean.im.postmaster.service.ConversationWriteFacade;
import com.cheeseocean.im.postmaster.service.DefaultMessagePolicyEngine;
import com.cheeseocean.im.postmaster.service.GroupMembershipFacade;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IngressEventListenerTest {

    private static ConversationSeqService.SeqBatch seqBatch(long start, long end) {
        return new ConversationSeqService.SeqBatch(new SequenceRange(start, end));
    }

    @Test
    void shouldPublishHistoryAndDeliveryForSingleChat() throws Exception {
        QueueAdapter queueAdapter = mock(QueueAdapter.class);
        GroupMembershipFacade groupMembershipFacade = mock(GroupMembershipFacade.class);
        ConversationSeqService conversationSeqService = mock(ConversationSeqService.class);
        ConversationWriteFacade conversationWriteFacade = mock(ConversationWriteFacade.class);
        when(conversationSeqService.allocateBatch("s:userA:userB", 1)).thenReturn(seqBatch(1001L, 1001L));

        IngressEventListener listener = listener(
                queueAdapter, groupMembershipFacade, conversationSeqService, conversationWriteFacade);

        listener.handle(List.of(singleMessage()));

        var historyCaptor = forClass(byte[].class);
        var deliveryCaptor = forClass(byte[].class);
        verify(queueAdapter).send(eq(TopicNames.HISTORY), eq("s:userA:userB"), historyCaptor.capture());
        verify(queueAdapter).send(eq(TopicNames.DELIVERY), eq("s:userA:userB"), deliveryCaptor.capture());

        HistoryEvent history = ProtoHistoryEventMapper.parse(historyCaptor.getValue());
        Message delivery = ProtoMessageMapper.fromProto(
                com.cheeseocean.im.common.api.protocol.proto.ProtoMessage.parseFrom(deliveryCaptor.getValue()));

        assertEquals(1001L, history.getBeginSeq());
        assertEquals(1001L, history.getEndSeq());
        assertEquals(1000L, history.getLastMaxSeq());
        assertEquals(1001L, history.getMessages().get(0).getSeq());
        assertEquals("userB", delivery.getReceiverId());
        assertEquals(1001L, delivery.getSeq());
    }

    @Test
    void shouldBatchAllocateSeqsForMultipleMessages() throws Exception {
        QueueAdapter queueAdapter = mock(QueueAdapter.class);
        GroupMembershipFacade groupMembershipFacade = mock(GroupMembershipFacade.class);
        ConversationSeqService conversationSeqService = mock(ConversationSeqService.class);
        ConversationWriteFacade conversationWriteFacade = mock(ConversationWriteFacade.class);
        when(conversationSeqService.allocateBatch("s:userA:userB", 2)).thenReturn(seqBatch(10L, 11L));

        IngressEventListener listener = listener(
                queueAdapter, groupMembershipFacade, conversationSeqService, conversationWriteFacade);

        listener.handle(List.of(singleMessage(), singleMessage()));

        var historyCaptor = forClass(byte[].class);
        verify(queueAdapter).send(eq(TopicNames.HISTORY), eq("s:userA:userB"), historyCaptor.capture());
        HistoryEvent history = ProtoHistoryEventMapper.parse(historyCaptor.getValue());

        assertEquals(10L, history.getBeginSeq());
        assertEquals(11L, history.getEndSeq());
        assertEquals(9L, history.getLastMaxSeq());
        assertEquals(2, history.getMessages().size());
        assertEquals(10L, history.getMessages().get(0).getSeq());
        assertEquals(11L, history.getMessages().get(1).getSeq());
        verify(queueAdapter, times(2)).send(eq(TopicNames.DELIVERY), eq("s:userA:userB"), org.mockito.ArgumentMatchers.any(byte[].class));
    }

    @Test
    void shouldPublishGroupHistoryAndDelivery() {
        QueueAdapter queueAdapter = mock(QueueAdapter.class);
        GroupMembershipFacade groupMembershipFacade = mock(GroupMembershipFacade.class);
        ConversationSeqService conversationSeqService = mock(ConversationSeqService.class);
        ConversationWriteFacade conversationWriteFacade = mock(ConversationWriteFacade.class);
        when(conversationSeqService.allocateBatch("g:crew", 1)).thenReturn(seqBatch(2002L, 2002L));

        IngressEventListener listener = listener(
                queueAdapter, groupMembershipFacade, conversationSeqService, conversationWriteFacade);

        listener.handle(List.of(groupMessage()));

        verify(queueAdapter).send(eq(TopicNames.HISTORY), eq("g:crew"), org.mockito.ArgumentMatchers.any(byte[].class));
        verify(queueAdapter).send(eq(TopicNames.DELIVERY), eq("g:crew"), org.mockito.ArgumentMatchers.any(byte[].class));
    }

    @Test
    void shouldSkipDeliveryWhenOnlinePushIsDisabled() {
        QueueAdapter queueAdapter = mock(QueueAdapter.class);
        GroupMembershipFacade groupMembershipFacade = mock(GroupMembershipFacade.class);
        ConversationSeqService conversationSeqService = mock(ConversationSeqService.class);
        ConversationWriteFacade conversationWriteFacade = mock(ConversationWriteFacade.class);
        when(conversationSeqService.allocateBatch("s:userA:userB", 1)).thenReturn(seqBatch(1001L, 1001L));

        IngressEventListener listener = listener(
                queueAdapter, groupMembershipFacade, conversationSeqService, conversationWriteFacade);

        Message message = singleMessage();
        message.getOptions().setNeedOnlinePush(false);

        listener.handle(List.of(message));

        verify(queueAdapter).send(eq(TopicNames.HISTORY), eq("s:userA:userB"), org.mockito.ArgumentMatchers.any(byte[].class));
        verify(queueAdapter, never()).send(eq(TopicNames.DELIVERY), eq("s:userA:userB"), org.mockito.ArgumentMatchers.any(byte[].class));
    }

    @Test
    void shouldCreateSingleConversationWhenFirstMessageArrives() {
        QueueAdapter queueAdapter = mock(QueueAdapter.class);
        GroupMembershipFacade groupMembershipFacade = mock(GroupMembershipFacade.class);
        ConversationSeqService conversationSeqService = mock(ConversationSeqService.class);
        ConversationWriteFacade conversationWriteFacade = mock(ConversationWriteFacade.class);
        when(conversationSeqService.allocateBatch("s:userA:userB", 1)).thenReturn(seqBatch(1L, 1L));

        IngressEventListener listener = listener(
                queueAdapter, groupMembershipFacade, conversationSeqService, conversationWriteFacade);

        listener.handle(List.of(singleMessage()));

        verify(conversationWriteFacade).createSingleChatConversation(
                "userA", "userB", "s:userA:userB", SessionType.SINGLE.getCode());
    }

    @Test
    void shouldCreateGroupConversationWhenFirstGroupMessageArrives() {
        QueueAdapter queueAdapter = mock(QueueAdapter.class);
        GroupMembershipFacade groupMembershipFacade = mock(GroupMembershipFacade.class);
        ConversationSeqService conversationSeqService = mock(ConversationSeqService.class);
        ConversationWriteFacade conversationWriteFacade = mock(ConversationWriteFacade.class);
        when(groupMembershipFacade.loadGroupMembers("crew")).thenReturn(List.of("u1", "u2", "u3"));
        when(conversationSeqService.allocateBatch("g:crew", 1)).thenReturn(seqBatch(1L, 1L));

        IngressEventListener listener = listener(
                queueAdapter, groupMembershipFacade, conversationSeqService, conversationWriteFacade);

        listener.handle(List.of(groupMessage()));

        verify(groupMembershipFacade).loadGroupMembers("crew");
        verify(conversationWriteFacade).createGroupChatConversations("crew", "g:crew", List.of("u1", "u2", "u3"));
    }

    @Test
    void shouldUseNotificationConversationForNotificationMessages() throws Exception {
        QueueAdapter queueAdapter = mock(QueueAdapter.class);
        GroupMembershipFacade groupMembershipFacade = mock(GroupMembershipFacade.class);
        ConversationSeqService conversationSeqService = mock(ConversationSeqService.class);
        ConversationWriteFacade conversationWriteFacade = mock(ConversationWriteFacade.class);
        when(conversationSeqService.allocateBatch("n:userB", 1)).thenReturn(seqBatch(1L, 1L));

        IngressEventListener listener = listener(
                queueAdapter, groupMembershipFacade, conversationSeqService, conversationWriteFacade);

        listener.handle(List.of(notificationMessage()));

        var historyCaptor = forClass(byte[].class);
        verify(queueAdapter).send(eq(TopicNames.HISTORY), eq("n:userB"), historyCaptor.capture());
        verify(queueAdapter).send(eq(TopicNames.DELIVERY), eq("n:userB"), org.mockito.ArgumentMatchers.any(byte[].class));
        verify(conversationWriteFacade).createSingleChatConversation(
                "system", "userB", "n:userB", SessionType.NOTIFICATION.getCode());

        HistoryEvent history = ProtoHistoryEventMapper.parse(historyCaptor.getValue());
        assertEquals("n:userB", history.getConversationId());
        assertEquals(1L, history.getMessages().get(0).getSeq());
    }

    @Test
    void shouldKeepReadReceiptAsTransientWhenHistoryIsDisabled() {
        QueueAdapter queueAdapter = mock(QueueAdapter.class);
        GroupMembershipFacade groupMembershipFacade = mock(GroupMembershipFacade.class);
        ConversationSeqService conversationSeqService = mock(ConversationSeqService.class);
        ConversationWriteFacade conversationWriteFacade = mock(ConversationWriteFacade.class);

        IngressEventListener listener = listener(
                queueAdapter, groupMembershipFacade, conversationSeqService, conversationWriteFacade);

        Message message = readReceiptMessage();
        message.getOptions().setNeedHistory(false);
        message.getOptions().setNeedOnlinePush(false);

        listener.handle(List.of(message));

        verify(conversationSeqService, never()).allocateBatch(eq("s:userA:userB"), anyInt());
        verify(queueAdapter, never()).send(eq(TopicNames.HISTORY), eq("s:userA:userB"), org.mockito.ArgumentMatchers.any(byte[].class));
        verify(queueAdapter, never()).send(eq(TopicNames.DELIVERY), eq("s:userA:userB"), org.mockito.ArgumentMatchers.any(byte[].class));
    }

    @Test
    void shouldEncodeOriginalContentInDeliveryPayload() throws Exception {
        QueueAdapter queueAdapter = mock(QueueAdapter.class);
        GroupMembershipFacade groupMembershipFacade = mock(GroupMembershipFacade.class);
        ConversationSeqService conversationSeqService = mock(ConversationSeqService.class);
        ConversationWriteFacade conversationWriteFacade = mock(ConversationWriteFacade.class);
        when(conversationSeqService.allocateBatch("s:userA:userB", 1)).thenReturn(seqBatch(77L, 77L));

        IngressEventListener listener = listener(
                queueAdapter, groupMembershipFacade, conversationSeqService, conversationWriteFacade);

        Message message = singleMessage();
        listener.handle(List.of(message));

        var deliveryCaptor = forClass(byte[].class);
        verify(queueAdapter).send(eq(TopicNames.DELIVERY), eq("s:userA:userB"), deliveryCaptor.capture());
        Message actual = ProtoMessageMapper.fromProto(
                com.cheeseocean.im.common.api.protocol.proto.ProtoMessage.parseFrom(deliveryCaptor.getValue()));

        assertArrayEquals("hello".getBytes(), actual.getContent());
        assertEquals(77L, actual.getSeq());
    }

    private static IngressEventListener listener(QueueAdapter queueAdapter,
                                                 GroupMembershipFacade groupMembershipFacade,
                                                 ConversationSeqService conversationSeqService,
                                                 ConversationWriteFacade conversationWriteFacade) {
        return new IngressEventListener(
                new MessageProducer(queueAdapter),
                new HistoryEventProducer(queueAdapter),
                groupMembershipFacade,
                conversationSeqService,
                new DefaultMessagePolicyEngine(),
                conversationWriteFacade
        );
    }

    private static Message singleMessage() {
        MessageOptions options = new MessageOptions();
        options.setNeedHistory(true);
        options.setNeedOnlinePush(true);
        options.setNeedOfflinePush(true);
        options.setSenderSync(false);

        Message message = new Message();
        message.setClientMsgId("client-single");
        message.setServerMsgId("msg-single");
        message.setSenderId("userA");
        message.setReceiverId("userB");
        message.setSessionType(SessionType.SINGLE);
        message.setContentType(ContentType.TEXT);
        message.setContent("hello".getBytes());
        message.setOptions(options);
        return message;
    }

    private static Message groupMessage() {
        MessageOptions options = new MessageOptions();
        options.setNeedHistory(true);
        options.setNeedOnlinePush(true);
        options.setNeedOfflinePush(true);
        options.setSenderSync(true);

        Message message = new Message();
        message.setClientMsgId("client-group");
        message.setServerMsgId("msg-group");
        message.setSenderId("captain");
        message.setGroupId("crew");
        message.setSessionType(SessionType.GROUP);
        message.setContentType(ContentType.TEXT);
        message.setContent("assemble".getBytes());
        message.setOptions(options);
        return message;
    }

    private static Message notificationMessage() {
        MessageOptions options = new MessageOptions();
        options.setNeedHistory(true);
        options.setNeedOnlinePush(true);
        options.setNeedOfflinePush(false);
        options.setNeedUnreadCount(false);
        options.setNeedLastMessage(false);
        options.setNotification(true);

        Message message = new Message();
        message.setClientMsgId("client-notify");
        message.setServerMsgId("msg-notify");
        message.setSenderId("system");
        message.setReceiverId("userB");
        message.setSessionType(SessionType.NOTIFICATION);
        message.setContentType(ContentType.SYSTEM_NOTIFY);
        message.setContent("{\"text\":\"you have a new follower\"}".getBytes());
        message.setOptions(options);
        return message;
    }

    private static Message readReceiptMessage() {
        MessageOptions options = new MessageOptions();
        options.setNeedConversation(true);

        Message message = new Message();
        message.setClientMsgId("client-read");
        message.setSenderId("userA");
        message.setReceiverId("userB");
        message.setSessionType(SessionType.SINGLE);
        message.setContentType(ContentType.READ_RECEIPT);
        message.setContent("{\"receiptType\":\"READ_CURSOR\",\"seq\":19}".getBytes());
        message.setOptions(options);
        return message;
    }
}
