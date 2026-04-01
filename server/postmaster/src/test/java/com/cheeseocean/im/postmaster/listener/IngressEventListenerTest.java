package com.cheeseocean.im.postmaster.listener;

import com.cheeseocean.im.common.api.dto.message.MessageOptions;
import com.cheeseocean.im.common.api.dto.message.SequencedMessage;
import com.cheeseocean.im.common.api.event.DeliveryEvent;
import com.cheeseocean.im.common.api.event.HistoryEvent;
import com.cheeseocean.im.common.api.event.IngressEvent;
import com.cheeseocean.im.common.core.constants.TopicNames;
import com.cheeseocean.im.common.core.enums.ContentType;
import com.cheeseocean.im.common.core.enums.SessionType;
import com.cheeseocean.im.common.core.queue.QueueAdapter;
import com.cheeseocean.im.common.core.store.sequence.SequenceRange;
import com.cheeseocean.im.common.api.conversation.ConversationSyncCommand;
import com.cheeseocean.im.postmaster.service.ConversationSeqService;
import com.cheeseocean.im.postmaster.service.ConversationSyncFacade;
import com.cheeseocean.im.postmaster.service.ConversationWriteFacade;
import com.cheeseocean.im.postmaster.service.DefaultMessagePolicyEngine;
import com.cheeseocean.im.postmaster.service.GroupFanoutPlanner;
import com.cheeseocean.im.postmaster.service.GroupMembershipFacade;
import com.cheeseocean.im.postmaster.service.MessageStateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IngressEventListenerTest {

    private static ConversationSeqService.SeqBatch seqBatch(long start, long end) {
        return new ConversationSeqService.SeqBatch(new SequenceRange(start, end));
    }

    @Test
    void ingressListenerShouldPublishHistoryAndDeliveryForSingleChat() {
        QueueAdapter queueAdapter = mock(QueueAdapter.class);
        GroupMembershipFacade groupMembershipFacade = mock(GroupMembershipFacade.class);
        ConversationSeqService conversationSeqService = mock(ConversationSeqService.class);
        MessageStateService messageStateService = mock(MessageStateService.class);
        when(conversationSeqService.allocateBatch("c1:userA:userB", 1))
                .thenReturn(seqBatch(1001L, 1001L));

        IngressEventListener listener = listener(queueAdapter, groupMembershipFacade,
                conversationSeqService, messageStateService);

        listener.handle(List.of(singleIngressEvent()));

        var historyCaptor  = forClass(HistoryEvent.class);
        var deliveryCaptor = forClass(DeliveryEvent.class);
        verify(queueAdapter).send(eq(TopicNames.HISTORY),  eq("c1:userA:userB"), historyCaptor.capture());
        verify(queueAdapter).send(eq(TopicNames.DELIVERY), eq("c1:userA:userB"), deliveryCaptor.capture());

        HistoryEvent history = historyCaptor.getValue();
        assertEquals(1001L, history.getBeginSeq());
        assertEquals(1001L, history.getEndSeq());
        assertEquals(1000L, history.getLastMaxSeq());

        SequencedMessage message = history.getMessages().get(0);
        assertEquals(1001L, message.getSeq());
        assertEquals("userB", deliveryCaptor.getValue().getTargetUserIds().get(0));
    }

    @Test
    void ingressListenerShouldBatchAllocateSeqsForMultipleEvents() {
        QueueAdapter queueAdapter = mock(QueueAdapter.class);
        GroupMembershipFacade groupMembershipFacade = mock(GroupMembershipFacade.class);
        ConversationSeqService conversationSeqService = mock(ConversationSeqService.class);
        MessageStateService messageStateService = mock(MessageStateService.class);
        when(conversationSeqService.allocateBatch("c1:userA:userB", 2))
                .thenReturn(seqBatch(10L, 11L));

        IngressEventListener listener = listener(queueAdapter, groupMembershipFacade,
                conversationSeqService, messageStateService);

        listener.handle(List.of(singleIngressEvent(), singleIngressEvent()));

        var historyCaptor = forClass(HistoryEvent.class);
        verify(queueAdapter).send(eq(TopicNames.HISTORY), eq("c1:userA:userB"), historyCaptor.capture());
        HistoryEvent history = historyCaptor.getValue();
        assertEquals(10L, history.getBeginSeq());
        assertEquals(11L, history.getEndSeq());
        assertEquals(9L,  history.getLastMaxSeq());
        assertEquals(2, history.getMessages().size());
        assertEquals(10L, history.getMessages().get(0).getSeq());
        assertEquals(11L, history.getMessages().get(1).getSeq());
    }

    @Test
    void ingressListenerShouldSplitGroupMembersIntoBatchesAndPublishDelivery() {
        QueueAdapter queueAdapter = mock(QueueAdapter.class);
        GroupMembershipFacade groupMembershipFacade = mock(GroupMembershipFacade.class);
        ConversationSeqService conversationSeqService = mock(ConversationSeqService.class);
        MessageStateService messageStateService = mock(MessageStateService.class);
        when(groupMembershipFacade.loadDeliveryTargets("c2:crew")).thenReturn(List.of("u1", "u2", "u3"));
        when(conversationSeqService.allocateBatch("c2:crew", 1))
                .thenReturn(seqBatch(2002L, 2002L));

        IngressEventListener listener = listener(queueAdapter, groupMembershipFacade,
                conversationSeqService, messageStateService);

        listener.handle(List.of(groupIngressEvent()));

        verify(groupMembershipFacade).loadDeliveryTargets("c2:crew");
        verify(queueAdapter).send(eq(TopicNames.HISTORY),  eq("c2:crew"), any(HistoryEvent.class));
        verify(queueAdapter, times(2))
                .send(eq(TopicNames.DELIVERY), eq("c2:crew"), any(DeliveryEvent.class));
    }

    @Test
    void ingressListenerShouldSkipDeliveryWhenOnlinePushIsDisabled() {
        QueueAdapter queueAdapter = mock(QueueAdapter.class);
        GroupMembershipFacade groupMembershipFacade = mock(GroupMembershipFacade.class);
        ConversationSeqService conversationSeqService = mock(ConversationSeqService.class);
        MessageStateService messageStateService = mock(MessageStateService.class);
        when(conversationSeqService.allocateBatch("c1:userA:userB", 1))
                .thenReturn(seqBatch(1001L, 1001L));

        IngressEventListener listener = listener(queueAdapter, groupMembershipFacade,
                conversationSeqService, messageStateService);

        IngressEvent event = singleIngressEvent();
        event.getOptions().setNeedOnlinePush(false);

        listener.handle(List.of(event));

        verify(queueAdapter).send(eq(TopicNames.HISTORY), eq("c1:userA:userB"), any(HistoryEvent.class));
        verify(queueAdapter, times(0))
                .send(eq(TopicNames.DELIVERY), eq("c1:userA:userB"), any(DeliveryEvent.class));
    }

    @Test
    void ingressListenerShouldAddSenderToDeliveryTargetsWhenSenderSyncEnabled() {
        QueueAdapter queueAdapter = mock(QueueAdapter.class);
        GroupMembershipFacade groupMembershipFacade = mock(GroupMembershipFacade.class);
        ConversationSeqService conversationSeqService = mock(ConversationSeqService.class);
        MessageStateService messageStateService = mock(MessageStateService.class);
        when(conversationSeqService.allocateBatch("c1:userA:userB", 1))
                .thenReturn(seqBatch(1002L, 1002L));

        IngressEventListener listener = listener(queueAdapter, groupMembershipFacade,
                conversationSeqService, messageStateService);

        IngressEvent event = singleIngressEvent();
        event.getOptions().setSenderSync(true);

        var deliveryCaptor = forClass(DeliveryEvent.class);
        listener.handle(List.of(event));

        verify(queueAdapter).send(eq(TopicNames.DELIVERY), eq("c1:userA:userB"), deliveryCaptor.capture());
        assertEquals(List.of("userB", "userA"), deliveryCaptor.getValue().getTargetUserIds());
    }

    @Test
    void ingressListenerShouldApplyMessageStatePolicy() {
        QueueAdapter queueAdapter = mock(QueueAdapter.class);
        GroupMembershipFacade groupMembershipFacade = mock(GroupMembershipFacade.class);
        ConversationSeqService conversationSeqService = mock(ConversationSeqService.class);
        MessageStateService messageStateService = mock(MessageStateService.class);
        when(conversationSeqService.allocateBatch("c1:userA:userB", 1))
                .thenReturn(seqBatch(1003L, 1003L));

        IngressEventListener listener = listener(queueAdapter, groupMembershipFacade,
                conversationSeqService, messageStateService);

        IngressEvent event = singleIngressEvent();
        event.getOptions().setNeedConversation(true);
        event.getOptions().setNeedUnreadCount(true);
        event.getOptions().setNeedLastMessage(true);

        listener.handle(List.of(event));

        // Storage messages now go through applyBatch, not apply
        verify(messageStateService).applyBatch(any());
    }

    @Test
    void readReceiptShouldTriggerPreProcessAsASideEffect() {
        // READ_RECEIPT 消息先触发已读 seq 缓存写入（旁路副作用），
        // 然后继续参与后续完整管道（categorize → seq → history → 投递）。
        // 本用例中 needHistory=false、needOnlinePush=false，因此该消息落入 notStorageCtx，
        // 不触发 seq 分配、不产生 history/delivery 事件——这是策略决定的结果，
        // 而非因为 READ_RECEIPT 被提前过滤掉。
        QueueAdapter queueAdapter = mock(QueueAdapter.class);
        GroupMembershipFacade groupMembershipFacade = mock(GroupMembershipFacade.class);
        ConversationSeqService conversationSeqService = mock(ConversationSeqService.class);
        MessageStateService messageStateService = mock(MessageStateService.class);

        IngressEventListener listener = listener(queueAdapter, groupMembershipFacade,
                conversationSeqService, messageStateService);

        listener.handle(List.of(readReceiptIngressEvent()));

        // 旁路副作用：processReadReceipts 应被调用一次
        var receiptCaptor = forClass(List.class);
        verify(messageStateService).processReadReceipts(receiptCaptor.capture());
        assertEquals(1, receiptCaptor.getValue().size());

        // 该事件 needHistory=false → storageCtx 为空 → 无 seq 分配、无 history、无 delivery
        verify(conversationSeqService, times(0)).allocateBatch(any(), eq(1));
        verify(queueAdapter, times(0)).send(eq(TopicNames.HISTORY),  any(), any());
        verify(queueAdapter, times(0)).send(eq(TopicNames.DELIVERY), any(), any());
    }

    @Test
    void readReceiptWithNeedHistoryShouldGoThroughFullPipeline() {
        // 当 READ_RECEIPT 携带 needHistory=true 时，消息会分配 seq、写入 history、投递，
        // 与普通消息完全一致（已读预处理只是旁路副作用，不影响主管道）。
        QueueAdapter queueAdapter = mock(QueueAdapter.class);
        GroupMembershipFacade groupMembershipFacade = mock(GroupMembershipFacade.class);
        ConversationSeqService conversationSeqService = mock(ConversationSeqService.class);
        MessageStateService messageStateService = mock(MessageStateService.class);
        when(conversationSeqService.allocateBatch("c1:userA:userB", 1))
                .thenReturn(seqBatch(50L, 50L));

        IngressEventListener listener = listener(queueAdapter, groupMembershipFacade,
                conversationSeqService, messageStateService);

        IngressEvent event = readReceiptIngressEvent();
        event.getOptions().setNeedHistory(true);
        event.getOptions().setNeedOnlinePush(true);

        listener.handle(List.of(event));

        // 旁路副作用仍触发
        verify(messageStateService).processReadReceipts(any());
        // seq 分配、history 发布、delivery 投递均应正常执行
        verify(conversationSeqService).allocateBatch("c1:userA:userB", 1);
        verify(queueAdapter).send(eq(TopicNames.HISTORY),  eq("c1:userA:userB"), any(HistoryEvent.class));
        verify(queueAdapter).send(eq(TopicNames.DELIVERY), eq("c1:userA:userB"), any(DeliveryEvent.class));
    }

    @Test
    void firstMessageShouldTriggerNewConversationSync() {
        QueueAdapter queueAdapter = mock(QueueAdapter.class);
        GroupMembershipFacade groupMembershipFacade = mock(GroupMembershipFacade.class);
        ConversationSeqService conversationSeqService = mock(ConversationSeqService.class);
        MessageStateService messageStateService = mock(MessageStateService.class);
        ConversationWriteFacade conversationWriteFacade = mock(ConversationWriteFacade.class);
        ConversationSyncFacade conversationSyncService = mock(ConversationSyncFacade.class);
        // seq starts at 1 → isNewConversation == true
        when(conversationSeqService.allocateBatch("c1:userA:userB", 1))
                .thenReturn(seqBatch(1L, 1L));

        IngressEventListener listener = new IngressEventListener(
                new ObjectMapper(),
                queueAdapter,
                groupMembershipFacade,
                new GroupFanoutPlanner(2),
                conversationSeqService,
                new DefaultMessagePolicyEngine(),
                messageStateService,
                conversationWriteFacade,
                conversationSyncService);

        listener.handle(List.of(singleIngressEvent()));

        verify(conversationWriteFacade).createSingleChatConversation(
                "userA", "userB", "c1:userA:userB", SessionType.SINGLE.getCode());
        verify(conversationSyncService, times(0)).createIfNew(any());
        var cmdCaptor = forClass(ConversationSyncCommand.class);
        verify(conversationSyncService).sync(cmdCaptor.capture());
        var cmd = cmdCaptor.getValue();
        assertEquals(true,  cmd.newConversation());
        assertEquals(1L,    cmd.latestMessage().getSeq());
        assertEquals(1,     cmd.senderIds().size());
        assertEquals("userA", cmd.senderIds().get(0));
        // participants must include both sender and receiver
        assertEquals(true, cmd.allParticipants().contains("userA"));
        assertEquals(true, cmd.allParticipants().contains("userB"));
    }

    @Test
    void subsequentBatchShouldSyncWithCorrectUnreadDelta() {
        QueueAdapter queueAdapter = mock(QueueAdapter.class);
        GroupMembershipFacade groupMembershipFacade = mock(GroupMembershipFacade.class);
        ConversationSeqService conversationSeqService = mock(ConversationSeqService.class);
        MessageStateService messageStateService = mock(MessageStateService.class);
        ConversationSyncFacade conversationSyncService = mock(ConversationSyncFacade.class);
        ConversationWriteFacade conversationWriteFacade = mock(ConversationWriteFacade.class);
        // seq starts at 5 → not a new conversation
        when(conversationSeqService.allocateBatch("c1:userA:userB", 2))
                .thenReturn(seqBatch(5L, 6L));

        IngressEventListener listener = new IngressEventListener(
                new ObjectMapper(),
                queueAdapter,
                groupMembershipFacade,
                new GroupFanoutPlanner(2),
                conversationSeqService,
                new DefaultMessagePolicyEngine(),
                messageStateService,
                conversationWriteFacade,
                conversationSyncService);

        listener.handle(List.of(singleIngressEvent(), singleIngressEvent()));

        verify(conversationWriteFacade, times(0)).createSingleChatConversation(any(), any(), any(), anyInt());
        verify(conversationSyncService, times(0)).createIfNew(any());
        var cmdCaptor = forClass(ConversationSyncCommand.class);
        verify(conversationSyncService).sync(cmdCaptor.capture());
        var cmd = cmdCaptor.getValue();
        assertEquals(false, cmd.newConversation());
        assertEquals(6L, cmd.latestMessage().getSeq());
        // both messages sent by userA → senderIds = ["userA","userA"]
        assertEquals(2, cmd.senderIds().size());
    }

    @Test
    void 通知消息首条应显式创建通知会话并同步offset() {
        // 通知消息首条也需要创建通知会话，只是参与者只有接收方；
        // 仍不走普通聊天的 applyBatch，但会创建通知会话并推进 offset。
        QueueAdapter queueAdapter = mock(QueueAdapter.class);
        GroupMembershipFacade groupMembershipFacade = mock(GroupMembershipFacade.class);
        ConversationSeqService conversationSeqService = mock(ConversationSeqService.class);
        MessageStateService messageStateService = mock(MessageStateService.class);
        ConversationSyncFacade conversationSyncService = mock(ConversationSyncFacade.class);
        ConversationWriteFacade conversationWriteFacade = mock(ConversationWriteFacade.class);
        // buildNotificationConversationId(NOTIFICATION, recvId="userB", groupId=null) → "c3:userB"
        when(conversationSeqService.allocateBatch("c3:userB", 1))
                .thenReturn(seqBatch(1L, 1L));

        IngressEventListener listener = new IngressEventListener(
                new ObjectMapper(),
                queueAdapter,
                groupMembershipFacade,
                new GroupFanoutPlanner(2),
                conversationSeqService,
                new DefaultMessagePolicyEngine(),
                messageStateService,
                conversationWriteFacade,
                conversationSyncService);

        listener.handle(List.of(notificationIngressEvent()));

        // 使用通知会话计数器分配 seq（c3:recvId，独立于聊天计数器）
        verify(conversationSeqService, times(1)).allocateBatch(eq("c3:userB"), anyInt());
        // history 事件正常发布（用于持久化到 MongoDB）
        verify(queueAdapter, times(1)).send(eq(TopicNames.HISTORY), eq("c3:userB"), any(HistoryEvent.class));
        // 推送正常触发
        verify(queueAdapter, times(1)).send(eq(TopicNames.DELIVERY), any(), any());
        verify(conversationWriteFacade).createSingleChatConversation(
                "system", "userB", "c3:userB", SessionType.NOTIFICATION.getCode());
        verify(conversationSyncService, times(0)).createIfNew(any());
        var cmdCaptor = forClass(ConversationSyncCommand.class);
        verify(conversationSyncService, times(1)).sync(cmdCaptor.capture());
        assertEquals(List.of("userB"), cmdCaptor.getValue().allParticipants());
        // 普通聊天状态聚合器不应被调用
        verify(messageStateService, times(0)).applyBatch(any());
    }

    @Test
    void 群聊首条应按groupId加载成员并显式创建群会话() {
        QueueAdapter queueAdapter = mock(QueueAdapter.class);
        GroupMembershipFacade groupMembershipFacade = mock(GroupMembershipFacade.class);
        ConversationSeqService conversationSeqService = mock(ConversationSeqService.class);
        MessageStateService messageStateService = mock(MessageStateService.class);
        ConversationSyncFacade conversationSyncService = mock(ConversationSyncFacade.class);
        ConversationWriteFacade conversationWriteFacade = mock(ConversationWriteFacade.class);
        when(groupMembershipFacade.loadDeliveryTargets("c2:crew")).thenReturn(List.of("u1", "u2", "u3"));
        when(groupMembershipFacade.loadGroupMembers("crew")).thenReturn(List.of("u1", "u2", "u3"));
        when(conversationSeqService.allocateBatch("c2:crew", 1)).thenReturn(seqBatch(1L, 1L));

        IngressEventListener listener = new IngressEventListener(
                new ObjectMapper(),
                queueAdapter,
                groupMembershipFacade,
                new GroupFanoutPlanner(2),
                conversationSeqService,
                new DefaultMessagePolicyEngine(),
                messageStateService,
                conversationWriteFacade,
                conversationSyncService);

        listener.handle(List.of(groupIngressEvent()));

        verify(groupMembershipFacade).loadDeliveryTargets("c2:crew");
        verify(groupMembershipFacade).loadGroupMembers("crew");
        verify(conversationWriteFacade).createGroupChatConversations("crew", "c2:crew", List.of("u1", "u2", "u3"));
        verify(conversationSyncService, times(0)).createIfNew(any());
        var cmdCaptor = forClass(ConversationSyncCommand.class);
        verify(conversationSyncService).sync(cmdCaptor.capture());
        assertEquals(List.of("u1", "u2", "u3", "captain"), cmdCaptor.getValue().allParticipants());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static IngressEventListener listener(QueueAdapter queueAdapter,
                                                  GroupMembershipFacade groupMembershipFacade,
                                                  ConversationSeqService conversationSeqService,
                                                  MessageStateService messageStateService) {
        return new IngressEventListener(
                new ObjectMapper(),
                queueAdapter,
                groupMembershipFacade,
                new GroupFanoutPlanner(2),
                conversationSeqService,
                new DefaultMessagePolicyEngine(),
                messageStateService,
                mock(ConversationWriteFacade.class),
                mock(ConversationSyncFacade.class));
    }

    private static IngressEvent singleIngressEvent() {
        IngressEvent event = new IngressEvent();
        MessageOptions options = new MessageOptions();
        options.setNeedHistory(true);
        options.setNeedOnlinePush(true);
        options.setNeedOfflinePush(true);
        options.setSenderSync(false);
        event.setRequestId("req-single");
        event.setClientMsgId("client-single");
        event.setServerMsgId("msg-single");
        event.setSenderId("userA");
        event.setReceiverId("userB");
        event.setSessionType(SessionType.SINGLE.getCode());
        event.setContentType(ContentType.TEXT.getCode());
        event.setContent("hello");
        event.setOptions(options);
        return event;
    }

    private static IngressEvent groupIngressEvent() {
        IngressEvent event = new IngressEvent();
        MessageOptions options = new MessageOptions();
        options.setNeedHistory(true);
        options.setNeedOnlinePush(true);
        options.setNeedOfflinePush(true);
        options.setSenderSync(true);
        event.setRequestId("req-group");
        event.setClientMsgId("client-group");
        event.setServerMsgId("msg-group");
        event.setSenderId("captain");
        event.setGroupId("crew");
        event.setSessionType(SessionType.GROUP.getCode());
        event.setContentType(ContentType.TEXT.getCode());
        event.setContent("assemble");
        event.setOptions(options);
        return event;
    }

    private static IngressEvent notificationIngressEvent() {
        IngressEvent event = new IngressEvent();
        MessageOptions options = new MessageOptions();
        options.setNeedHistory(true);
        options.setNeedOnlinePush(true);
        options.setNeedOfflinePush(false);
        options.setNeedUnreadCount(false);  // 通知不计入未读
        options.setNeedLastMessage(false);  // 通知不更新 lastMessage
        options.setNotification(true);      // 标记为系统通知
        event.setRequestId("req-notify");
        event.setClientMsgId("client-notify");
        event.setServerMsgId("msg-notify");
        event.setSenderId("system");
        event.setReceiverId("userB");
        event.setSessionType(SessionType.NOTIFICATION.getCode());
        event.setContentType(ContentType.SYSTEM_NOTIFY.getCode());
        event.setContent("{\"text\":\"you have a new follower\"}");
        event.setOptions(options);
        return event;
    }

    private static IngressEvent readReceiptIngressEvent() {
        IngressEvent event = new IngressEvent();
        MessageOptions options = new MessageOptions();
        options.setNeedConversation(true);
        event.setRequestId("req-read");
        event.setClientMsgId("client-read");
        event.setSenderId("userA");
        event.setReceiverId("userB");
        event.setSessionType(SessionType.SINGLE.getCode());
        event.setContentType(ContentType.READ_RECEIPT.getCode());
        event.setContent("{\"receiptType\":\"READ_CURSOR\",\"seq\":19}");
        event.setOptions(options);
        return event;
    }
}
