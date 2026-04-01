package com.cheeseocean.im.postmaster.listener;

import com.cheeseocean.im.common.api.dto.message.SequencedMessage;
import com.cheeseocean.im.common.api.event.DeliveryEvent;
import com.cheeseocean.im.common.api.event.HistoryEvent;
import com.cheeseocean.im.common.api.event.IngressEvent;
import com.cheeseocean.im.common.core.constants.TopicNames;
import com.cheeseocean.im.common.core.enums.ContentType;
import com.cheeseocean.im.common.core.enums.SessionType;
import com.cheeseocean.im.common.core.util.ConversationIdUtil;
import com.cheeseocean.im.common.api.conversation.ConversationSyncCommand;
import com.cheeseocean.im.postmaster.service.ConversationSeqService;
import com.cheeseocean.im.postmaster.service.ConversationSyncFacade;
import com.cheeseocean.im.postmaster.service.ConversationWriteFacade;
import com.cheeseocean.im.postmaster.service.GroupFanoutPlanner;
import com.cheeseocean.im.postmaster.service.GroupMembershipFacade;
import com.cheeseocean.im.postmaster.service.MessagePolicyEngine;
import com.cheeseocean.im.postmaster.service.MessageRouteDecision;
import com.cheeseocean.im.postmaster.service.MessageStateService;
import com.cheeseocean.im.postmaster.service.MessageWithTargets;
import com.cheeseocean.im.common.core.queue.QueueAdapter;
import com.cheeseocean.im.common.core.queue.annotation.QueueListener;
import com.cheeseocean.im.common.core.logging.CommonLoggers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class IngressEventListener {

    private static final Logger log = CommonLoggers.POSTMASTER;

    /** 路由决策结果与原始事件的绑定，避免对同一条消息重复调用 decide() */
    private record EventCtx(IngressEvent event, MessageRouteDecision decision) {}

    /** 分配了 seq 并解析了投递目标的消息 */
    private record ProcessedMsg(IngressEvent event, MessageRouteDecision decision,
                                SequencedMessage message, List<String> targets) {}

    private final ObjectMapper objectMapper;
    private final QueueAdapter queueAdapter;
    private final GroupMembershipFacade groupMembershipFacade;
    private final GroupFanoutPlanner groupFanoutPlanner;
    private final ConversationSeqService conversationSeqService;
    private final MessagePolicyEngine messagePolicyEngine;
    private final MessageStateService messageStateService;
    private final ConversationWriteFacade conversationWriteService;
    private final ConversationSyncFacade conversationSyncService;

    public IngressEventListener(ObjectMapper objectMapper,
                                QueueAdapter queueAdapter,
                                GroupMembershipFacade groupMembershipFacade,
                                GroupFanoutPlanner groupFanoutPlanner,
                                ConversationSeqService conversationSeqService,
                                MessagePolicyEngine messagePolicyEngine,
                                MessageStateService messageStateService,
                                ConversationWriteFacade conversationWriteService,
                                ConversationSyncFacade conversationSyncService) {
        this.objectMapper = objectMapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.queueAdapter = queueAdapter;
        this.groupMembershipFacade = groupMembershipFacade;
        this.groupFanoutPlanner = groupFanoutPlanner;
        this.conversationSeqService = conversationSeqService;
        this.messagePolicyEngine = messagePolicyEngine;
        this.messageStateService = messageStateService;
        this.conversationWriteService = conversationWriteService;
        this.conversationSyncService = conversationSyncService;
    }

    // 消费 INGRESS 队列，批量接收同一会话的消息
    @QueueListener(topic = TopicNames.INGRESS, group = "postman-ingress", concurrency = 1, batch = true, batchSize = 500)
    public void onMessage(List<IngressEvent> events) {
        try {
            handle(events);
        } catch (Exception e) {
            log.error("处理 ingress 事件批次失败: {}", events, e);
        }
    }

    // 包级可见，供测试直接调用。
    // 批次按 ConversationIdUtil.buildQueueKey 分组（single 和 notification 共享同一 key），
    // 同一批次可同时含 regular 和 notification 消息，
    // 二者路由到各自独立的处理方法，各自在方法内计算 conversationId。
    void handle(List<IngressEvent> events) {
        if (events == null || events.isEmpty()) return;

        // 已读回执旁路：提前将已读 seq 写入 Redis，消息本身继续走完整管道
        preProcessReadReceipts(events);

        // 四路分类
        List<EventCtx> storageMsgList      = new ArrayList<>();
        List<EventCtx> transientMsgList    = new ArrayList<>();
        List<EventCtx> storageNotifyList   = new ArrayList<>();
        List<EventCtx> transientNotifyList = new ArrayList<>();
        for (IngressEvent event : events) {
            MessageRouteDecision d = messagePolicyEngine.decide(event);
            if (d.notification()) {
                (d.persistHistory() ? storageNotifyList : transientNotifyList).add(new EventCtx(event, d));
            } else {
                (d.persistHistory() ? storageMsgList : transientMsgList).add(new EventCtx(event, d));
            }
        }

        handleMessage(storageMsgList, transientMsgList);
        handleNotification(storageNotifyList, transientNotifyList);
    }

    // 处理普通聊天消息
    // conversationId 在此处由 ConversationIdUtil.buildConversationId 计算
    private void handleMessage(List<EventCtx> storageList, List<EventCtx> transientList) {
        if (storageList.isEmpty() && transientList.isEmpty()) return;

        IngressEvent sample = firstEvent(storageList, transientList);
        String conversationId = ConversationIdUtil.buildConversationId(
                sample.getSessionType(), sample.getSenderId(), sample.getReceiverId(), sample.getGroupId());

        pushTransient(transientList, conversationId);
        if (storageList.isEmpty()) return;

        ConversationSeqService.SeqBatch seqBatch =
                conversationSeqService.allocateBatch(conversationId, storageList.size());
        List<ProcessedMsg> processed = bindSeqs(storageList, seqBatch.range().startInclusive(), conversationId);

        messageStateService.applyBatch(processed.stream()
                .map(p -> new MessageWithTargets(p.message(), p.targets()))
                .toList());

        // 首次会话需为用户创建会话状态
        createConversationIfNeeded(sample, conversationId, seqBatch.isNewConversation());

        publishHistoryEvent(conversationId, seqBatch, processed);

        for (ProcessedMsg p : processed) {
            if (p.decision().sendDelivery()) sendDelivery(p.message(), p.targets(), p.event());
        }
    }

    // ── handleNotification ───────────────────────────────────────────────────
    // 处理通知消息
    // conversationId 在此处由 ConversationIdUtil.buildNotificationConversationId 计算，
    // 与聊天会话使用独立的 seq 计数器
    private void handleNotification(List<EventCtx> storageList, List<EventCtx> transientList) {
        if (storageList.isEmpty() && transientList.isEmpty()) return;

        IngressEvent sample = firstEvent(storageList, transientList);
        String conversationId = ConversationIdUtil.buildNotificationConversationId(
                sample.getSessionType(), sample.getReceiverId(), sample.getGroupId());

        pushTransient(transientList, conversationId);
        if (storageList.isEmpty()) return;

        ConversationSeqService.SeqBatch seqBatch =
                conversationSeqService.allocateBatch(conversationId, storageList.size());
        List<ProcessedMsg> processed = bindSeqs(storageList, seqBatch.range().startInclusive(), conversationId);

        publishHistoryEvent(conversationId, seqBatch, processed);

        createConversationIfNeeded(sample, conversationId, seqBatch.isNewConversation());

        for (ProcessedMsg p : processed) {
            if (p.decision().sendDelivery()) sendDelivery(p.message(), p.targets(), p.event());
        }
    }

    // ── 共用私有方法 ──────────────────────────────────────────────────────────

    private static IngressEvent firstEvent(List<EventCtx> storageList, List<EventCtx> transientList) {
        return !storageList.isEmpty() ? storageList.get(0).event() : transientList.get(0).event();
    }

    private void preProcessReadReceipts(List<IngressEvent> events) {
        List<IngressEvent> readReceipts = new ArrayList<>();
        for (IngressEvent event : events) {
            if (event.getContentType() != null
                    && event.getContentType() == ContentType.READ_RECEIPT.getCode()) {
                readReceipts.add(event);
            }
        }
        if (!readReceipts.isEmpty()) {
            messageStateService.processReadReceipts(readReceipts);
        }
    }

    private void pushTransient(List<EventCtx> transientList, String conversationId) {
        for (EventCtx ctx : transientList) {
            if (!ctx.decision().sendDelivery()) continue;
            SequencedMessage msg = toSequencedMessage(ctx.event(), null, conversationId);
            List<String> targets = resolveTargets(msg, ctx.decision());
            messageStateService.apply(msg, targets);
            sendDelivery(msg, targets, ctx.event());
        }
    }

    private List<ProcessedMsg> bindSeqs(List<EventCtx> ctxList, long startSeq, String conversationId) {
        List<ProcessedMsg> result = new ArrayList<>(ctxList.size());
        long seq = startSeq;
        for (EventCtx ctx : ctxList) {
            SequencedMessage msg = toSequencedMessage(ctx.event(), seq++, conversationId);
            List<String> targets = resolveTargets(msg, ctx.decision());
            result.add(new ProcessedMsg(ctx.event(), ctx.decision(), msg, targets));
        }
        return result;
    }

    private void publishHistoryEvent(String conversationId,
                                     ConversationSeqService.SeqBatch seqBatch,
                                     List<ProcessedMsg> processed) {
        HistoryEvent historyEvent = new HistoryEvent();
        historyEvent.setConversationId(conversationId);
        historyEvent.setLastMaxSeq(seqBatch.lastMaxSeq());
        historyEvent.setBeginSeq(seqBatch.range().startInclusive());
        historyEvent.setEndSeq(seqBatch.range().endInclusive());
        historyEvent.setMessages(processed.stream().map(ProcessedMsg::message).toList());
        queueAdapter.send(TopicNames.HISTORY, conversationId, historyEvent);
    }

    private ConversationSyncCommand buildSyncCommand(String conversationId,
                                                     ConversationSeqService.SeqBatch seqBatch,
                                                     List<ProcessedMsg> processed) {
        Set<String> participantSet = resolveConversationParticipants(processed);
        List<String> senderIds    = new ArrayList<>(processed.size());
        for (ProcessedMsg p : processed) {
            senderIds.add(p.message().getSenderId());
        }
        SequencedMessage latestMessage = processed.get(processed.size() - 1).message();
        return new ConversationSyncCommand(
                conversationId,
                latestMessage.getSessionType() != null ? latestMessage.getSessionType() : 0,
                seqBatch.isNewConversation(),
                latestMessage,
                List.copyOf(participantSet),
                List.copyOf(senderIds)
        );
    }

    private void sendDelivery(SequencedMessage message, List<String> targets, IngressEvent event) {
        if (event.getSessionType() != null && event.getSessionType() == SessionType.GROUP.getCode()) {
            for (List<String> batch : groupFanoutPlanner.partition(targets)) {
                DeliveryEvent deliveryEvent = new DeliveryEvent();
                deliveryEvent.setConversationId(message.getConversationId());
                deliveryEvent.setMessage(message);
                deliveryEvent.setTargetUserIds(batch);
                queueAdapter.send(TopicNames.DELIVERY, message.getConversationId(), deliveryEvent);
            }
        } else {
            DeliveryEvent deliveryEvent = new DeliveryEvent();
            deliveryEvent.setConversationId(message.getConversationId());
            deliveryEvent.setMessage(message);
            deliveryEvent.setTargetUserIds(targets);
            queueAdapter.send(TopicNames.DELIVERY, message.getConversationId(), deliveryEvent);
        }
    }

    private Set<String> resolveConversationParticipants(List<ProcessedMsg> processed) {
        Set<String> participants = new LinkedHashSet<>();
        if (processed == null || processed.isEmpty()) {
            return participants;
        }
        SequencedMessage sample = processed.get(0).message();
        Integer sessionType = sample.getSessionType();
        if (sessionType != null && sessionType == SessionType.GROUP.getCode()) {
            participants.addAll(groupMembershipFacade.loadGroupMembers(sample.getGroupId()));
            if (sample.getSenderId() != null) {
                participants.add(sample.getSenderId());
            }
            return participants;
        }
        if (sessionType != null && sessionType == SessionType.NOTIFICATION.getCode()) {
            if (sample.getRecvId() != null) {
                participants.add(sample.getRecvId());
            }
            return participants;
        }
        if (sample.getRecvId() != null) {
            participants.add(sample.getRecvId());
        }
        if (sample.getSenderId() != null) {
            participants.add(sample.getSenderId());
        }
        return participants;
    }

    private void createConversationIfNeeded(IngressEvent sample, String conversationId, boolean newConversation) {
        if (!newConversation) {
            return;
        }
        if (sample.getSessionType() != null && sample.getSessionType() == SessionType.GROUP.getCode()) {
            List<String> userIds = groupMembershipFacade.loadGroupMembers(sample.getGroupId());
            conversationWriteService.createGroupChatConversations(sample.getGroupId(), conversationId, userIds);
            return;
        }
        conversationWriteService.createSingleChatConversation(
                sample.getSenderId(),
                sample.getReceiverId(),
                conversationId,
                sample.getSessionType() == null ? 0 : sample.getSessionType()
        );
    }

    private List<String> resolveTargets(SequencedMessage message, MessageRouteDecision decision) {
        if (message.getSessionType() != null && message.getSessionType() == SessionType.GROUP.getCode()) {
            return groupMembershipFacade.loadDeliveryTargets(message.getConversationId());
        }
        if (decision.senderSync() && message.getSenderId() != null) {
            return List.of(message.getRecvId(), message.getSenderId());
        }
        return List.of(message.getRecvId());
    }

    // conversationId 由调用方计算传入，不从 event.getConversationId() 读取
    private SequencedMessage toSequencedMessage(IngressEvent event, Long seq, String conversationId) {
        SequencedMessage message = new SequencedMessage();
        message.setConversationId(conversationId);
        message.setSeq(seq);
        message.setClientMsgId(event.getClientMsgId());
        message.setServerMsgId(event.getServerMsgId());
        message.setSenderId(event.getSenderId());
        message.setRecvId(event.getReceiverId());
        message.setGroupId(event.getGroupId());
        message.setSessionType(event.getSessionType());
        message.setContentType(event.getContentType());
        message.setContent(event.getContent());
        message.setSendTime(event.getSendTime());
        message.setOptions(event.getOptions());
        message.setExt(event.getExt());
        return message;
    }
}
