package com.cheeseocean.im.postmaster.listener;

import com.cheeseocean.im.common.api.conversation.ConversationService;
import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.dto.message.MessageOptions;
import com.cheeseocean.im.common.api.enums.ContentType;
import com.cheeseocean.im.common.api.enums.ChatType;
import com.cheeseocean.im.common.api.event.HistoryEvent;
import com.cheeseocean.im.common.api.protocol.ProtoHistoryEventMapper;
import com.cheeseocean.im.common.api.protocol.ProtoMessageMapper;
import com.cheeseocean.im.common.core.constants.TopicNames;
import com.cheeseocean.im.common.core.queue.KeyedMessage;
import com.cheeseocean.im.common.core.queue.QueueAdapter;
import com.cheeseocean.im.common.core.store.sequence.SequenceRange;
import com.cheeseocean.im.common.core.store.conversation.ConversationStateStore;
import com.cheeseocean.im.postmaster.sender.HistoryEventProducer;
import com.cheeseocean.im.postmaster.sender.MessageProducer;
import com.cheeseocean.im.postmaster.service.ConversationSeqService;
import com.cheeseocean.im.postmaster.service.DefaultMessagePolicyEngine;
import com.cheeseocean.im.postmaster.service.GroupMembershipFacade;
import com.cheeseocean.im.postmaster.service.UserMaxSeqPersistenceWriter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
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
        when(conversationSeqService.allocateBatch("s:userA:userB", 1)).thenReturn(seqBatch(1001L, 1001L));

        IngressEventListener listener = listener(
                queueAdapter, groupMembershipFacade, conversationSeqService);

        listener.handle(List.of(singleMessage()));

        var historyCaptor = forClass(byte[].class);
        verify(queueAdapter).send(eq(TopicNames.HISTORY), eq("s:userA:userB"), historyCaptor.capture());
        List<KeyedMessage<byte[]>> deliveries = captureDeliveryBatch(queueAdapter);

        HistoryEvent history = ProtoHistoryEventMapper.parse(historyCaptor.getValue());
        Message delivery = ProtoMessageMapper.fromProto(
                com.cheeseocean.im.common.api.protocol.proto.ProtoMessage.parseFrom(deliveries.get(0).payload()));

        assertEquals(1001L, history.getBeginSeq());
        assertEquals(1001L, history.getEndSeq());
        assertEquals(1000L, history.getLastMaxSeq());
        assertEquals(1001L, history.getMessages().get(0).getSeq());
        assertEquals("userB", delivery.getReceiverId());
        assertEquals(1001L, delivery.getSeq());
    }

    @Test
    void shouldAtomicallyAdvanceDirectParticipantMaxSeqAndReceiverUnread() {
        QueueAdapter queueAdapter = mock(QueueAdapter.class);
        ConversationSeqService conversationSeqService = mock(ConversationSeqService.class);
        ConversationStateStore stateStore = mock(ConversationStateStore.class);
        UserMaxSeqPersistenceWriter writer = mock(UserMaxSeqPersistenceWriter.class);
        when(conversationSeqService.allocateBatch("s:userA:userB", 1)).thenReturn(seqBatch(1001L, 1001L));
        IngressEventListener listener = new IngressEventListener(
                new MessageProducer(queueAdapter), new HistoryEventProducer(queueAdapter),
                mock(GroupMembershipFacade.class), conversationSeqService,
                new DefaultMessagePolicyEngine(), new com.cheeseocean.im.postmaster.service.GroupFanoutPlanner(500),
                stateStore, writer);

        listener.handle(List.of(singleMessage()));

        verify(stateStore).advanceUserMaxSeq("userA", "s:userA:userB", 1001L, false);
        verify(stateStore).advanceUserMaxSeq("userB", "s:userA:userB", 1001L, true);
        verify(writer).enqueue("userA", "s:userA:userB", 1001L);
        verify(writer).enqueue("userB", "s:userA:userB", 1001L);
        verify(stateStore).setConversationMaxSeq("s:userA:userB", 1001L);
    }

    @Test
    void shouldBatchAllocateSeqsForMultipleMessages() throws Exception {
        QueueAdapter queueAdapter = mock(QueueAdapter.class);
        GroupMembershipFacade groupMembershipFacade = mock(GroupMembershipFacade.class);
        ConversationSeqService conversationSeqService = mock(ConversationSeqService.class);
        when(conversationSeqService.allocateBatch("s:userA:userB", 2)).thenReturn(seqBatch(10L, 11L));

        IngressEventListener listener = listener(
                queueAdapter, groupMembershipFacade, conversationSeqService);

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
        List<KeyedMessage<byte[]>> deliveries = captureDeliveryBatch(queueAdapter);
        assertEquals(2, deliveries.size());
        assertEquals("s:userA:userB", deliveries.get(0).key());
        assertEquals("s:userA:userB", deliveries.get(1).key());
    }

    @Test
    void shouldPublishGroupHistoryAndFanoutPerMemberForNormalGroup() throws Exception {
        QueueAdapter queueAdapter = mock(QueueAdapter.class);
        GroupMembershipFacade groupMembershipFacade = mock(GroupMembershipFacade.class);
        ConversationSeqService conversationSeqService = mock(ConversationSeqService.class);
        when(conversationSeqService.allocateBatch("g:crew", 1)).thenReturn(seqBatch(2002L, 2002L));
        when(groupMembershipFacade.loadGroupType("crew")).thenReturn(com.cheeseocean.im.common.api.enums.GroupTypeEnum.NORMAL_GROUP);
        when(groupMembershipFacade.loadGroupMembers("crew")).thenReturn(List.of("u1", "u2", "u3"));

        IngressEventListener listener = listener(
                queueAdapter, groupMembershipFacade, conversationSeqService);

        listener.handle(List.of(groupMessage()));

        // history 仍是单条会话级 event
        verify(queueAdapter).send(eq(TopicNames.HISTORY), eq("g:crew"), org.mockito.ArgumentMatchers.any(byte[].class));
        // delivery 改为写扩散：每位成员一份 keyed DeliveryEvent，key 形如 g:{groupId}:{memberId}
        List<KeyedMessage<byte[]>> deliveries = captureDeliveryBatch(queueAdapter);
        assertEquals(List.of("g:crew:u1", "g:crew:u2", "g:crew:u3"),
                deliveries.stream().map(KeyedMessage::key).toList());

        // 校验 delivery payload 中 receiverId 被替换为对应成员（而非群本身的 receiverId）
        for (int i = 0; i < 3; i++) {
            Message msg = ProtoMessageMapper.fromProto(
                    com.cheeseocean.im.common.api.protocol.proto.ProtoMessage.parseFrom(deliveries.get(i).payload()));
            assertEquals("u" + (i + 1), msg.getReceiverId());
            assertEquals(ChatType.GROUP, msg.getChatType());
            assertEquals("crew", msg.getGroupId());
            assertEquals(2002L, msg.getSeq());
        }
    }

    @Test
    void shouldPublishGroupHistoryButSkipDeliveryForSuperGroup() {
        QueueAdapter queueAdapter = mock(QueueAdapter.class);
        GroupMembershipFacade groupMembershipFacade = mock(GroupMembershipFacade.class);
        ConversationSeqService conversationSeqService = mock(ConversationSeqService.class);
        when(conversationSeqService.allocateBatch("g:crew", 1)).thenReturn(seqBatch(2002L, 2002L));
        when(groupMembershipFacade.loadGroupType("crew")).thenReturn(com.cheeseocean.im.common.api.enums.GroupTypeEnum.SUPER_GROUP);

        IngressEventListener listener = listener(
                queueAdapter, groupMembershipFacade, conversationSeqService);

        listener.handle(List.of(groupMessage()));

        verify(queueAdapter).send(eq(TopicNames.HISTORY), eq("g:crew"), org.mockito.ArgumentMatchers.any(byte[].class));
        verify(queueAdapter, never()).sendBatch(eq(TopicNames.DELIVERY), anyList());
        verify(groupMembershipFacade, never()).loadGroupMembers("crew");
    }

    @Test
    void shouldFallbackToWriteFanoutWhenGroupTypeUnknown() {
        QueueAdapter queueAdapter = mock(QueueAdapter.class);
        GroupMembershipFacade groupMembershipFacade = mock(GroupMembershipFacade.class);
        ConversationSeqService conversationSeqService = mock(ConversationSeqService.class);
        when(conversationSeqService.allocateBatch("g:crew", 1)).thenReturn(seqBatch(2002L, 2002L));
        when(groupMembershipFacade.loadGroupType("crew")).thenReturn(null);
        when(groupMembershipFacade.loadGroupMembers("crew")).thenReturn(List.of("u1", "u2"));

        IngressEventListener listener = listener(
                queueAdapter, groupMembershipFacade, conversationSeqService);

        listener.handle(List.of(groupMessage()));

        assertEquals(2, captureDeliveryBatch(queueAdapter).size());
    }

    @Test
    void shouldSkipDeliveryWhenOnlinePushIsDisabled() {
        QueueAdapter queueAdapter = mock(QueueAdapter.class);
        GroupMembershipFacade groupMembershipFacade = mock(GroupMembershipFacade.class);
        ConversationSeqService conversationSeqService = mock(ConversationSeqService.class);
        when(conversationSeqService.allocateBatch("s:userA:userB", 1)).thenReturn(seqBatch(1001L, 1001L));

        IngressEventListener listener = listener(
                queueAdapter, groupMembershipFacade, conversationSeqService);

        Message message = singleMessage();
        message.getOptions().setNeedOnlinePush(false);

        listener.handle(List.of(message));

        verify(queueAdapter).send(eq(TopicNames.HISTORY), eq("s:userA:userB"), org.mockito.ArgumentMatchers.any(byte[].class));
        verify(queueAdapter, never()).sendBatch(eq(TopicNames.DELIVERY), anyList());
    }

    @Test
    void shouldCreateSingleConversationWhenFirstMessageArrives() {
        QueueAdapter queueAdapter = mock(QueueAdapter.class);
        GroupMembershipFacade groupMembershipFacade = mock(GroupMembershipFacade.class);
        ConversationSeqService conversationSeqService = mock(ConversationSeqService.class);
        ConversationService conversationService = mock(ConversationService.class);
        when(conversationSeqService.allocateBatch("s:userA:userB", 1)).thenReturn(seqBatch(1L, 1L));

        IngressEventListener listener = listener(
                queueAdapter, groupMembershipFacade, conversationSeqService, conversationService);

        listener.handle(List.of(singleMessage()));

        verify(conversationService).createSingleChatConversation(
                "userA", "userB", "s:userA:userB", ChatType.PRIVATE.getCode());
    }

    @Test
    void shouldCreateGroupConversationWhenFirstGroupMessageArrives() {
        QueueAdapter queueAdapter = mock(QueueAdapter.class);
        GroupMembershipFacade groupMembershipFacade = mock(GroupMembershipFacade.class);
        ConversationSeqService conversationSeqService = mock(ConversationSeqService.class);
        ConversationService conversationService = mock(ConversationService.class);
        when(groupMembershipFacade.loadGroupType("crew")).thenReturn(com.cheeseocean.im.common.api.enums.GroupTypeEnum.NORMAL_GROUP);
        when(groupMembershipFacade.loadGroupMembers("crew")).thenReturn(List.of("u1", "u2", "u3"));
        when(conversationSeqService.allocateBatch("g:crew", 1)).thenReturn(seqBatch(1L, 1L));

        IngressEventListener listener = listener(
                queueAdapter, groupMembershipFacade, conversationSeqService, conversationService);

        listener.handle(List.of(groupMessage()));

        // 首条群消息：一次用于 createConversationIfNeeded，一次用于 fanout
        verify(groupMembershipFacade, times(2)).loadGroupMembers("crew");
        verify(conversationService).createGroupChatConversations("crew", "g:crew", List.of("u1", "u2", "u3"));
    }

    @Test
    void shouldUseNotificationConversationForNotificationMessages() throws Exception {
        QueueAdapter queueAdapter = mock(QueueAdapter.class);
        GroupMembershipFacade groupMembershipFacade = mock(GroupMembershipFacade.class);
        ConversationSeqService conversationSeqService = mock(ConversationSeqService.class);
        when(conversationSeqService.allocateBatch("n:userB", 1)).thenReturn(seqBatch(1L, 1L));

        IngressEventListener listener = listener(
                queueAdapter, groupMembershipFacade, conversationSeqService);

        listener.handle(List.of(notificationMessage()));

        var historyCaptor = forClass(byte[].class);
        verify(queueAdapter).send(eq(TopicNames.HISTORY), eq("n:userB"), historyCaptor.capture());
        List<KeyedMessage<byte[]>> deliveries = captureDeliveryBatch(queueAdapter);
        assertEquals("n:userB", deliveries.get(0).key());

        HistoryEvent history = ProtoHistoryEventMapper.parse(historyCaptor.getValue());
        assertEquals("n:userB", history.getConversationId());
        assertEquals(1L, history.getMessages().get(0).getSeq());
    }

    @Test
    void shouldDiscardLegacyReadReceiptBecauseChatReadIsTheOnlyReadPath() {
        QueueAdapter queueAdapter = mock(QueueAdapter.class);
        GroupMembershipFacade groupMembershipFacade = mock(GroupMembershipFacade.class);
        ConversationSeqService conversationSeqService = mock(ConversationSeqService.class);

        IngressEventListener listener = listener(
                queueAdapter, groupMembershipFacade, conversationSeqService);

        Message message = readReceiptMessage();
        message.getOptions().setNeedHistory(false);
        message.getOptions().setNeedOnlinePush(false);

        listener.handle(List.of(message));

        verify(conversationSeqService, never()).allocateBatch(eq("s:userA:userB"), anyInt());
        verify(queueAdapter, never()).send(eq(TopicNames.HISTORY), eq("s:userA:userB"), org.mockito.ArgumentMatchers.any(byte[].class));
        verify(queueAdapter, never()).sendBatch(eq(TopicNames.DELIVERY), anyList());
    }

    @Test
    void shouldEncodeOriginalContentInDeliveryPayload() throws Exception {
        QueueAdapter queueAdapter = mock(QueueAdapter.class);
        GroupMembershipFacade groupMembershipFacade = mock(GroupMembershipFacade.class);
        ConversationSeqService conversationSeqService = mock(ConversationSeqService.class);
        when(conversationSeqService.allocateBatch("s:userA:userB", 1)).thenReturn(seqBatch(77L, 77L));

        IngressEventListener listener = listener(
                queueAdapter, groupMembershipFacade, conversationSeqService);

        Message message = singleMessage();
        listener.handle(List.of(message));

        List<KeyedMessage<byte[]>> deliveries = captureDeliveryBatch(queueAdapter);
        Message actual = ProtoMessageMapper.fromProto(
                com.cheeseocean.im.common.api.protocol.proto.ProtoMessage.parseFrom(deliveries.get(0).payload()));

        assertArrayEquals("hello".getBytes(), actual.getContent());
        assertEquals(77L, actual.getSeq());
    }

    @Test
    void shouldPropagateIngressFailureToQueueContainer() {
        QueueAdapter queueAdapter = mock(QueueAdapter.class);
        GroupMembershipFacade groupMembershipFacade = mock(GroupMembershipFacade.class);
        ConversationSeqService conversationSeqService = mock(ConversationSeqService.class);
        when(conversationSeqService.allocateBatch("s:userA:userB", 1))
                .thenThrow(new IllegalStateException("sequence unavailable"));

        IngressEventListener listener = listener(
                queueAdapter, groupMembershipFacade, conversationSeqService);

        assertThrows(IllegalStateException.class, () -> listener.onMessage(List.of(singleMessage())));
    }

    @Test
    void shouldPropagateGroupMemberQueryFailureForQueueRetry() {
        QueueAdapter queueAdapter = mock(QueueAdapter.class);
        GroupMembershipFacade groupMembershipFacade = mock(GroupMembershipFacade.class);
        ConversationSeqService conversationSeqService = mock(ConversationSeqService.class);
        when(conversationSeqService.allocateBatch("g:crew", 1)).thenReturn(seqBatch(2002L, 2002L));
        when(groupMembershipFacade.loadGroupType("crew"))
                .thenReturn(com.cheeseocean.im.common.api.enums.GroupTypeEnum.NORMAL_GROUP);
        when(groupMembershipFacade.loadGroupMembers("crew"))
                .thenThrow(new IllegalStateException("membership unavailable"));

        IngressEventListener listener = listener(
                queueAdapter, groupMembershipFacade, conversationSeqService);

        assertThrows(IllegalStateException.class, () -> listener.onMessage(List.of(groupMessage())));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<KeyedMessage<byte[]>> captureDeliveryBatch(QueueAdapter queueAdapter) {
        ArgumentCaptor<List<KeyedMessage<byte[]>>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(queueAdapter).sendBatch(eq(TopicNames.DELIVERY), captor.capture());
        return captor.getValue();
    }

    private static IngressEventListener listener(QueueAdapter queueAdapter,
                                                 GroupMembershipFacade groupMembershipFacade,
                                                 ConversationSeqService conversationSeqService) {
        return listener(queueAdapter, groupMembershipFacade, conversationSeqService,
                mock(ConversationService.class));
    }

    private static IngressEventListener listener(QueueAdapter queueAdapter,
                                                 GroupMembershipFacade groupMembershipFacade,
                                                 ConversationSeqService conversationSeqService,
                                                 ConversationService conversationService) {
        return new IngressEventListener(
                new MessageProducer(queueAdapter),
                new HistoryEventProducer(queueAdapter),
                groupMembershipFacade,
                conversationSeqService,
                new DefaultMessagePolicyEngine(),
                new com.cheeseocean.im.postmaster.service.GroupFanoutPlanner(500),
                conversationService
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
        message.setChatType(ChatType.PRIVATE);
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
        message.setChatType(ChatType.GROUP);
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
        message.setChatType(ChatType.NOTIFICATION);
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
        message.setChatType(ChatType.PRIVATE);
        message.setContentType(ContentType.READ_RECEIPT);
        message.setContent("{\"receiptType\":\"READ_CURSOR\",\"seq\":19}".getBytes());
        message.setOptions(options);
        return message;
    }
}
