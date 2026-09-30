package com.cheeseocean.im.postmaster.listener;

import com.cheeseocean.im.common.api.conversation.ConversationService;
import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.dto.message.MessageOptions;
import com.cheeseocean.im.common.api.enums.ChatType;
import com.cheeseocean.im.common.api.enums.ContentType;
import com.cheeseocean.im.common.api.enums.GroupSendPermissionCode;
import com.cheeseocean.im.common.api.enums.GroupTypeEnum;
import com.cheeseocean.im.common.api.event.GroupFanoutEvent;
import com.cheeseocean.im.common.api.event.HistoryEvent;
import com.cheeseocean.im.common.api.group.GroupMemberPage;
import com.cheeseocean.im.common.api.permission.GroupMessageSendPermissionDecision;
import com.cheeseocean.im.common.api.permission.GroupMessageSendPermissionResult;
import com.cheeseocean.im.common.api.protocol.ProtoHistoryEventMapper;
import com.cheeseocean.im.common.api.protocol.ProtoMessageMapper;
import com.cheeseocean.im.common.api.protocol.proto.ProtoMessage;
import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.common.core.constants.TopicNames;
import com.cheeseocean.im.common.core.queue.KeyedMessage;
import com.cheeseocean.im.common.core.queue.QueueAdapter;
import com.cheeseocean.im.common.core.store.conversation.ConversationStateStore;
import com.cheeseocean.im.common.core.store.fanout.GroupFanoutJobStore;
import com.cheeseocean.im.common.core.store.idempotency.ingress.IngressMessageInboxStore;
import com.cheeseocean.im.common.core.store.sequence.SequenceRange;
import com.cheeseocean.im.common.core.util.ConversationIdUtil;
import com.cheeseocean.im.postmaster.sender.HistoryEventProducer;
import com.cheeseocean.im.postmaster.sender.MessageProducer;
import com.cheeseocean.im.postmaster.service.ConversationSeqService;
import com.cheeseocean.im.postmaster.service.DefaultMessagePolicyEngine;
import com.cheeseocean.im.postmaster.service.GroupFanoutPlanner;
import com.cheeseocean.im.postmaster.service.GroupMembershipFacade;
import com.cheeseocean.im.postmaster.service.UserMaxSeqPersistenceWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 验证真实事件编码与带租约/稳定 seq 的状态迁移，故障精确注入在发布边界。 */
class IngressCanonicalBatchTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void privateAndNotificationSharingProductionKeyKeepTheirOwnHistoryAndDelivery(boolean reverse) {
        Fixture f = new Fixture();
        Message chat = message("chat", ChatType.PRIVATE, "A", "B", false);
        Message notification = message("notification", ChatType.NOTIFICATION, "A", "B", true);
        assertEquals(queueKey(chat), queueKey(notification));

        f.listener.onMessage(ordered(reverse, chat, notification));

        assertEquals(Map.of("chat", "s:A:B", "notification", "n:B"), f.historyOwners());
        assertEquals(Map.of("chat", "s:A:B", "notification", "n:B"), f.deliveryOwners());
        assertEquals(1L, chat.getSeq());
        assertEquals(1L, notification.getSeq());
        f.inbox.assertCompleted(chat, notification);
        verify(f.conversations).createSingleChatConversation("A", "B", "s:A:B", ChatType.PRIVATE.getCode());
        verify(f.state).advanceUserMaxSeq("A", "s:A:B", 1L, 0);
        verify(f.state).advanceUserMaxSeq("B", "s:A:B", 1L, 1);
        verify(f.state, never()).advanceUserMaxSeq(anyString(), eq("n:B"), anyLong(), anyInt());
        int publications = f.publications;
        f.listener.onMessage(ordered(!reverse, chat, notification));
        assertEquals(publications, f.publications);
        verify(f.seqs).allocateBatch("s:A:B", 1);
        verify(f.seqs).allocateBatch("n:B", 1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void oppositeDirectionNotificationsUseReceiverInboxRegardlessOfFirstMessage(boolean reverse) {
        Fixture f = new Fixture();
        Message toB = message("to-B", ChatType.NOTIFICATION, "A", "B", true);
        Message toA = message("to-A", ChatType.NOTIFICATION, "B", "A", true);
        assertEquals(queueKey(toB), queueKey(toA));

        f.listener.onMessage(ordered(reverse, toB, toA));

        assertEquals(Map.of("to-B", "n:B", "to-A", "n:A"), f.historyOwners());
        assertEquals(f.historyOwners(), f.deliveryOwners());
        assertEquals(1L, toA.getSeq());
        assertEquals(1L, toB.getSeq());
        f.inbox.assertCompleted(toB, toA);
        verifyNoInteractions(f.conversations, f.state, f.writer);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void groupAndFlaggedGroupNotificationKeepSeparateHistoryFanoutAndMemberState(boolean reverse) {
        Fixture f = new Fixture();
        Message chat = message("group-chat", ChatType.GROUP, "A", null, false);
        Message notification = message("group-notification", ChatType.GROUP, "A", null, true);
        assertEquals(queueKey(chat), queueKey(notification));

        f.listener.onMessage(ordered(reverse, chat, notification));

        Map<String, String> expected = Map.of("group-chat", "g:crew", "group-notification", "ng:crew");
        assertEquals(expected, f.historyOwners());
        assertEquals(expected, f.fanoutOwners());
        assertEquals(2, f.fanouts.size());
        assertTrue(f.fanouts.stream().allMatch(GroupFanoutEvent::isCreateConversation));
        assertTrue(f.fanouts.stream().allMatch(event -> event.getMembershipVersion() == 7L));
        verify(f.membership).checkSendPermissions("crew", List.of("A"));
        verify(f.membership, never()).loadGroupMembers(anyString());
        f.inbox.assertCompleted(chat, notification);

        // 消费实际 JSON fanout 事件，核对成员 delivery key 保持兼容且状态归属不再被写死为 g:。
        GroupMemberPage page = new GroupMemberPage();
        page.setUserIds(List.of("A", "B"));
        when(f.membership.loadGroupMembersPage(eq("crew"), eq(7L), anyLong(), anyString(), anyString(), anyInt()))
                .thenReturn(page);
        GroupFanoutEventListener worker = new GroupFanoutEventListener(
                f.mapper, f.membership, f.planner, new MessageProducer(f.queue), f.state, f.writer,
                mock(GroupFanoutJobStore.class), 200, 60);
        ReflectionTestUtils.setField(worker, "conversationService", f.conversations);
        f.fanouts.forEach(worker::handle);
        for (String convId : List.of("g:crew", "ng:crew")) {
            verify(f.conversations).createGroupChatConversations("crew", convId, List.of("A", "B"));
            verify(f.state).advanceUserMaxSeq("A", convId, 1L, 0);
            verify(f.state).advanceUserMaxSeq("B", convId, 1L, 1);
            verify(f.writer).enqueue("B", convId, 1L);
        }
        assertEquals(4, f.deliveries.size());
        assertTrue(f.deliveries.stream().allMatch(delivery -> delivery.key().equals("g:crew:" + delivery.payload().getReceiverId())));
        assertEquals(List.of(1L, 1L, 1L, 1L), f.deliveries.stream().map(delivery -> delivery.payload().getSeq()).toList());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sameCanonicalInboxKeepsExecutionSemanticsAndInterleavedSequenceOrder(boolean reverse) {
        Fixture f = new Fixture();
        Message flaggedPrivate = message("flagged-private", ChatType.PRIVATE, "A", "B", true);
        Message pureNotification = message("pure-notification", ChatType.NOTIFICATION, "A", "B", false);
        Message laterFlaggedPrivate = message("later-private", ChatType.PRIVATE, "A", "B", true);

        f.listener.onMessage(ordered(reverse, flaggedPrivate, pureNotification, laterFlaggedPrivate));

        assertEquals(Map.of("flagged-private", "n:B", "pure-notification", "n:B", "later-private", "n:B"), f.historyOwners());
        assertEquals(List.of(1L, 2L, 3L), f.deliveries.stream().map(delivery -> delivery.payload().getSeq()).toList());
        // 保留已有 options.notification 对状态更新的分流，不由首条 PRIVATE 决定纯 NOTIFICATION 的语义。
        verify(f.state).advanceUserMaxSeq("A", "n:B", 2L, 0);
        verify(f.state).advanceUserMaxSeq("B", "n:B", 2L, 1);
        verify(f.state, times(1)).setConversationMaxSeq(eq("n:B"), anyLong());
        verifyNoInteractions(f.conversations);
        f.inbox.assertCompleted(flaggedPrivate, pureNotification, laterFlaggedPrivate);
    }

    @Test
    void partiallyAppendedDeliveryBatchReplaysInStableSequenceOrder() {
        Fixture f = new Fixture();
        Message first = message("first", ChatType.PRIVATE, "A", "B", false);
        Message second = message("second", ChatType.PRIVATE, "A", "B", false);
        f.failTopic = TopicNames.DELIVERY;
        f.failKey = "s:A:B";
        f.failAfterMatching = 1;

        assertThrows(IllegalStateException.class, () -> f.listener.onMessage(List.of(first, second)));

        assertEquals(1, f.deliveries.size());
        f.inbox.assertReleased(first, 1L);
        f.inbox.assertReleased(second, 2L);
        f.failTopic = null;
        f.listener.onMessage(List.of(
                message("second", ChatType.PRIVATE, "A", "B", false),
                message("first", ChatType.PRIVATE, "A", "B", false)));

        assertEquals(List.of(1L, 1L, 2L), f.deliveries.stream().map(delivery -> delivery.payload().getSeq()).toList());
        f.inbox.assertCompleted(first, second);
        verify(f.seqs).allocateBatch("s:A:B", 2);
        verify(f.state, times(2)).advanceUserMaxSeq("A", "s:A:B", 2L, 0);
        verify(f.state, times(2)).advanceUserMaxSeq("B", "s:A:B", 2L, 2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"bind", "complete"})
    void partialInboxMutationFailureRetainsStableSeqAndReleasesOwnedLease(String failure) {
        Fixture f = new Fixture();
        Message message = message("chat", ChatType.PRIVATE, "A", "B", false);
        if (failure.equals("bind")) {
            doAnswer(call -> {
                call.callRealMethod();
                throw new IllegalStateException("Lost seq binding response");
            }).when(f.inbox).bindSequences(anyList(), anyString());
        } else {
            doAnswer(call -> {
                call.callRealMethod();
                throw new IllegalStateException("Lost completion response");
            }).when(f.inbox).completeBatch(anyList(), anyString());
        }

        assertThrows(IllegalStateException.class, () -> f.listener.onMessage(List.of(message)));

        if (failure.equals("bind")) {
            f.inbox.assertReleased(message, 1L);
            assertEquals(0, f.publications);
            doCallRealMethod().when(f.inbox).bindSequences(anyList(), anyString());
        } else {
            f.inbox.assertCompleted(message);
            doCallRealMethod().when(f.inbox).completeBatch(anyList(), anyString());
        }
        f.listener.onMessage(List.of(message("chat", ChatType.PRIVATE, "A", "B", false)));

        f.inbox.assertCompleted(message);
        verify(f.seqs).allocateBatch("s:A:B", 1);
        assertEquals(1, f.deliveries.size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"history", "delivery"})
    void laterCanonicalGroupFailureCompletesEarlierGroupAndReplaysOnlyPendingSeq(String failure) {
        Fixture f = new Fixture();
        Message chat = message("chat", ChatType.PRIVATE, "A", "B", false);
        Message notification = message("notification", ChatType.NOTIFICATION, "A", "B", true);
        Message unvisited = message("unvisited", ChatType.NOTIFICATION, "B", "A", true);
        f.failTopic = failure.equals("history") ? TopicNames.HISTORY : TopicNames.DELIVERY;
        f.failKey = "n:B";

        assertThrows(IllegalStateException.class, () -> f.listener.onMessage(List.of(chat, notification, unvisited)));

        f.inbox.assertCompleted(chat);
        f.inbox.assertReleased(notification, 1L);
        f.inbox.assertReleased(unvisited, 0L);
        assertEquals(1, f.histories.stream().filter(event -> event.getConversationId().equals("s:A:B")).count());
        f.failTopic = null;
        // 重建 wire 消息，不能依赖上次调用给 Java 对象填入的 seq。
        Message replay = message("notification", ChatType.NOTIFICATION, "A", "B", true);
        f.listener.onMessage(List.of(unvisited, replay, chat));

        assertEquals(1L, replay.getSeq());
        assertEquals(1L, unvisited.getSeq());
        assertEquals(Map.of("chat", "s:A:B", "notification", "n:B", "unvisited", "n:A"), f.deliveryOwners());
        f.inbox.assertCompleted(chat, replay, unvisited);
        verify(f.seqs).allocateBatch("n:B", 1);
        verify(f.seqs).allocateBatch("s:A:B", 1);
        verify(f.seqs).allocateBatch("n:A", 1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void partialFanoutChunksReleaseEntireUnfinishedGroupAndReuseSeqAndJobIdentity(boolean reverse) {
        Fixture f = new Fixture();
        Message chat = message("group-chat", ChatType.GROUP, "A", null, false);
        Message notice1 = message("notice-1", ChatType.GROUP, "A", null, true);
        Message notice2 = message("notice-2", ChatType.GROUP, "A", null, true);
        f.failTopic = TopicNames.GROUP_FANOUT;
        f.failKey = "ng:crew";
        f.failAfterMatching = 1;

        List<Message> batch = reverse ? List.of(notice1, notice2, chat) : List.of(chat, notice1, notice2);
        assertThrows(IllegalStateException.class, () -> f.listener.onMessage(batch));

        if (reverse) f.inbox.assertReleased(chat, 0L);
        else f.inbox.assertCompleted(chat);
        f.inbox.assertReleased(notice1, 1L);
        f.inbox.assertReleased(notice2, 2L);
        GroupFanoutEvent acknowledged = f.fanouts.stream().filter(event -> event.getConversationId().equals("ng:crew")).findFirst().orElseThrow();
        f.failTopic = null;
        f.listener.onMessage(List.of(
                message("notice-2", ChatType.GROUP, "A", null, true),
                message("notice-1", ChatType.GROUP, "A", null, true), chat));

        f.inbox.assertCompleted(chat, notice1, notice2);
        verify(f.seqs).allocateBatch("ng:crew", 2);
        assertEquals(2, f.fanouts.stream().filter(event -> event.getJobId().equals(acknowledged.getJobId())).count());
        assertTrue(f.fanouts.stream().filter(event -> event.getConversationId().equals("ng:crew"))
                .allMatch(GroupFanoutEvent::isCreateConversation));
        assertEquals(Map.of("group-chat", "g:crew", "notice-1", "ng:crew", "notice-2", "ng:crew"), f.fanoutOwners());
    }

    @Test
    void filteringAndNoSideEffectMessagesDoNotLeaveClaimedInboxBehind() {
        Fixture f = new Fixture();
        Message legacy = message("legacy", ChatType.PRIVATE, "A", "B", false);
        legacy.setContentType(ContentType.READ_RECEIPT);
        Message silent = message("silent", ChatType.NOTIFICATION, "A", "B", true);
        silent.getOptions().setNeedHistory(false);
        silent.getOptions().setNeedOnlinePush(false);
        Message transientNotice = message("transient", ChatType.NOTIFICATION, "B", "A", true);
        transientNotice.getOptions().setNeedHistory(false);

        f.listener.onMessage(List.of(legacy, silent, transientNotice));

        assertFalse(f.inbox.states.containsKey(inboxKey(legacy)));
        f.inbox.assertCompleted(silent, transientNotice);
        assertTrue(f.histories.isEmpty());
        assertEquals(Map.of("transient", "n:A"), f.deliveryOwners());
        verifyNoInteractions(f.seqs);
        int publications = f.publications;
        f.listener.onMessage(List.of(silent, transientNotice));
        assertEquals(publications, f.publications);
    }

    @Test
    void acquiredSiblingsAreReleasedWhenClaimBatchFindsConflictingPayload() {
        Fixture f = new Fixture();
        Message original = message("notification", ChatType.NOTIFICATION, "A", "B", true);
        f.listener.onMessage(List.of(original));
        Message conflicting = message("notification", ChatType.NOTIFICATION, "A", "B", true);
        conflicting.setContent("changed".getBytes());
        Message sibling = message("sibling", ChatType.PRIVATE, "A", "B", false);

        assertThrows(IllegalStateException.class, () -> f.listener.onMessage(List.of(sibling, conflicting)));

        f.inbox.assertReleased(sibling, 0L);
        f.inbox.assertCompleted(original);
        f.listener.onMessage(List.of(sibling));
        f.inbox.assertCompleted(sibling);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void partiallyAcquiredClaimBatchIsReleasedWhenStoreThrowsOrReturnsIncompleteResult(boolean throwFailure) {
        Fixture f = new Fixture();
        Message first = message("first", ChatType.PRIVATE, "A", "B", false);
        Message second = message("second", ChatType.NOTIFICATION, "A", "B", true);
        doAnswer(call -> {
            List<?> claims = (List<?>) call.callRealMethod();
            if (throwFailure) throw new IllegalStateException("Claim response unavailable");
            return claims.subList(0, 1);
        }).when(f.inbox).claimBatch(anyList(), anyString(), anyLong());

        assertThrows(IllegalStateException.class, () -> f.listener.onMessage(List.of(first, second)));

        f.inbox.assertReleased(first, 0L);
        f.inbox.assertReleased(second, 0L);
        assertEquals(0, f.publications);
        verifyNoInteractions(f.seqs);
    }

    @Test
    void cleanupFailureDoesNotHideOriginalPublishFailure() {
        Fixture f = new Fixture();
        f.failTopic = TopicNames.HISTORY;
        f.failKey = "s:A:B";
        IllegalStateException cleanupFailure = new IllegalStateException("Release unavailable");
        doThrow(cleanupFailure).when(f.inbox).releaseBatch(anyList(), anyString());

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> f.listener.onMessage(List.of(message("chat", ChatType.PRIVATE, "A", "B", false))));

        assertEquals("Injected publish failure", failure.getMessage());
        assertArrayEquals(new Throwable[]{cleanupFailure}, failure.getSuppressed());
        assertEquals(0, f.publications);
    }

    private static List<Message> ordered(boolean reverse, Message... messages) {
        List<Message> result = new ArrayList<>(List.of(messages));
        if (reverse) Collections.reverse(result);
        return result;
    }

    private static Message message(String id, ChatType type, String sender, String receiver, boolean notification) {
        Message message = new Message();
        message.setServerMsgId(id);
        message.setClientMsgId("client-" + id);
        message.setChatType(type);
        message.setSenderId(sender);
        message.setReceiverId(receiver);
        if (type == ChatType.GROUP) message.setGroupId("crew");
        message.setContentType(ContentType.TEXT);
        message.setContent(id.getBytes());
        MessageOptions options = new MessageOptions();
        options.setNotification(notification);
        options.setNeedHistory(true);
        options.setNeedOnlinePush(true);
        message.setOptions(options);
        // 使用生产 ingress 相同的 Protobuf round-trip。
        return ProtoMessageMapper.fromProto(ProtoMessageMapper.toProto(message));
    }

    private static String queueKey(Message message) {
        return ConversationIdUtil.buildQueueKey(message.getChatType(), message.getSenderId(), message.getReceiverId(), message.getGroupId());
    }

    private static String inboxKey(Message message) {
        return RedisKeys.ingressMessageInbox(IngressMessageFingerprint.serverMessageId(message.getServerMsgId()));
    }

    private static class Fixture {
        final ObjectMapper mapper = new ObjectMapper();
        final QueueAdapter queue = mock(QueueAdapter.class);
        final ConversationSeqService seqs = mock(ConversationSeqService.class);
        final GroupMembershipFacade membership = mock(GroupMembershipFacade.class);
        final ConversationService conversations = mock(ConversationService.class);
        final ConversationStateStore state = mock(ConversationStateStore.class);
        final UserMaxSeqPersistenceWriter writer = mock(UserMaxSeqPersistenceWriter.class);
        final StatefulInbox inbox = spy(new StatefulInbox());
        final GroupFanoutPlanner planner = new GroupFanoutPlanner(200, 1, 524288);
        final List<HistoryEvent> histories = new ArrayList<>();
        final List<GroupFanoutEvent> fanouts = new ArrayList<>();
        final List<KeyedMessage<Message>> deliveries = new ArrayList<>();
        final IngressEventListener listener;
        String failTopic;
        String failKey;
        int failAfterMatching;
        int publications;

        Fixture() {
            Map<String, Long> maxSequences = new LinkedHashMap<>();
            when(seqs.allocateBatch(anyString(), anyInt())).thenAnswer(call -> {
                String convId = call.getArgument(0);
                int count = call.getArgument(1);
                long last = maxSequences.getOrDefault(convId, 0L);
                maxSequences.put(convId, last + count);
                return new ConversationSeqService.SeqBatch(new SequenceRange(last + 1L, last + count));
            });
            when(membership.checkSendPermissions(anyString(), anyList())).thenAnswer(call -> {
                GroupMessageSendPermissionResult result = new GroupMessageSendPermissionResult();
                result.setGroupId(call.getArgument(0));
                result.setGroupType(GroupTypeEnum.NORMAL_GROUP);
                result.setMembershipVersion(7L);
                List<String> senders = call.getArgument(1);
                result.setDecisions(senders.stream().map(sender -> GroupMessageSendPermissionDecision.of(sender, GroupSendPermissionCode.ALLOWED, 0L)).toList());
                return result;
            });
            doAnswer(call -> {
                String topic = call.getArgument(0);
                byte[] payload = call.getArgument(2);
                if (topic.equals(TopicNames.HISTORY)) {
                    HistoryEvent event = ProtoHistoryEventMapper.parse(payload);
                    failIfRequested(topic, event.getConversationId());
                    histories.add(event);
                } else if (topic.equals(TopicNames.GROUP_FANOUT)) {
                    GroupFanoutEvent event = mapper.readValue(payload, GroupFanoutEvent.class);
                    assertEquals("g:crew", call.getArgument(1));
                    failIfRequested(topic, event.getConversationId());
                    fanouts.add(event);
                } else fail("Unexpected topic: " + topic);
                publications++;
                return null;
            }).when(queue).send(anyString(), anyString(), any(byte[].class));
            doAnswer(call -> {
                List<KeyedMessage<byte[]>> batch = call.getArgument(1);
                for (KeyedMessage<byte[]> entry : batch) {
                    failIfRequested(TopicNames.DELIVERY, entry.key());
                    deliveries.add(new KeyedMessage<>(entry.key(), ProtoMessageMapper.fromProto(ProtoMessage.parseFrom(entry.payload()))));
                }
                publications++;
                return null;
            }).when(queue).sendBatch(eq(TopicNames.DELIVERY), anyList());
            listener = new IngressEventListener(new MessageProducer(queue), new HistoryEventProducer(queue), membership,
                    seqs, new DefaultMessagePolicyEngine(), planner, state, writer, inbox);
            ReflectionTestUtils.setField(listener, "conversationService", conversations);
        }

        void failIfRequested(String topic, String key) {
            if (topic.equals(failTopic) && key.equals(failKey) && failAfterMatching-- <= 0) {
                throw new IllegalStateException("Injected publish failure");
            }
        }

        Map<String, String> historyOwners() {
            Map<String, String> owners = new LinkedHashMap<>();
            for (HistoryEvent event : histories) {
                assertEquals(event.getMessages().get(0).getSeq(), event.getBeginSeq());
                assertEquals(event.getMessages().get(event.getMessages().size() - 1).getSeq(), event.getEndSeq());
                event.getMessages().forEach(message -> putOwner(owners, message, event.getConversationId()));
            }
            return owners;
        }

        Map<String, String> deliveryOwners() {
            Map<String, String> owners = new LinkedHashMap<>();
            deliveries.forEach(delivery -> putOwner(owners, delivery.payload(), delivery.key()));
            return owners;
        }

        Map<String, String> fanoutOwners() {
            Map<String, String> owners = new LinkedHashMap<>();
            fanouts.forEach(event -> event.getMessages().forEach(message -> putOwner(owners, message, event.getConversationId())));
            return owners;
        }

        void putOwner(Map<String, String> owners, Message message, String conversationId) {
            String previous = owners.put(message.getServerMsgId(), conversationId);
            if (previous != null) assertEquals(previous, conversationId);
            assertEquals(inbox.states.get(inboxKey(message)).seq, message.getSeq() == null ? 0L : message.getSeq());
        }
    }

    /** 严格校验 owner 与终态的内存 inbox；释放保留 seq，避免无状态 mock 掩盖重放错误。 */
    private static class StatefulInbox implements IngressMessageInboxStore {
        final Map<String, State> states = new LinkedHashMap<>();

        public List<Claim> claimBatch(List<ClaimRequest> requests, String owner, long now) {
            List<Claim> claims = new ArrayList<>();
            for (ClaimRequest request : requests) {
                State state = states.computeIfAbsent(request.key(), ignored -> new State(request.payloadFingerprint()));
                ClaimStatus status;
                if (!state.fingerprint.equals(request.payloadFingerprint())) status = ClaimStatus.CONFLICT;
                else if (state.completed) status = ClaimStatus.COMPLETED;
                else {
                    assertNull(state.owner, "上次失败的租约必须释放");
                    state.owner = owner;
                    status = ClaimStatus.ACQUIRED;
                }
                claims.add(new Claim(request.key(), status, state.seq, now + 30_000L));
            }
            return claims;
        }

        public Map<String, Long> bindSequences(List<SequenceBinding> bindings, String owner) {
            Map<String, Long> result = new LinkedHashMap<>();
            for (SequenceBinding binding : bindings) {
                State state = requireOwned(binding.key(), owner);
                if (state.seq == 0L) state.seq = binding.proposedSeq();
                result.put(binding.key(), state.seq);
            }
            return result;
        }

        public void completeBatch(List<String> keys, String owner) {
            keys.forEach(key -> {
                State state = requireOwned(key, owner);
                state.completed = true;
                state.owner = null;
            });
        }

        public void releaseBatch(List<String> keys, String owner) {
            keys.forEach(key -> {
                State state = states.get(key);
                if (state != null && owner.equals(state.owner)) state.owner = null;
            });
        }

        State requireOwned(String key, String owner) {
            State state = states.get(key);
            assertNotNull(state);
            assertFalse(state.completed);
            assertEquals(owner, state.owner);
            return state;
        }

        void assertCompleted(Message... messages) {
            for (Message message : messages) {
                State state = states.get(inboxKey(message));
                assertTrue(state.completed);
                assertNull(state.owner);
            }
        }

        void assertReleased(Message message, long seq) {
            State state = states.get(inboxKey(message));
            assertFalse(state.completed);
            assertNull(state.owner);
            assertEquals(seq, state.seq);
        }

        private static class State {
            final String fingerprint;
            String owner;
            long seq;
            boolean completed;

            State(String fingerprint) { this.fingerprint = fingerprint; }
        }
    }
}
