package com.cheeseocean.im.postmaster.listener;

import com.cheeseocean.im.common.api.conversation.ConversationService;
import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.enums.ContentType;
import com.cheeseocean.im.common.api.enums.ChatType;
import com.cheeseocean.im.common.api.enums.GroupTypeEnum;
import com.cheeseocean.im.common.api.event.HistoryEvent;
import com.cheeseocean.im.common.core.constants.TopicNames;
import com.cheeseocean.im.common.core.logging.CommonLoggers;
import com.cheeseocean.im.common.core.metrics.ImMetrics;
import com.cheeseocean.im.common.core.queue.annotation.QueueListener;
import com.cheeseocean.im.common.core.queue.KeyedMessage;
import com.cheeseocean.im.common.core.util.ConversationIdUtil;
import com.cheeseocean.im.common.core.store.conversation.ConversationStateStore;
import com.cheeseocean.im.postmaster.service.UserMaxSeqPersistenceWriter;
import com.cheeseocean.im.postmaster.sender.HistoryEventProducer;
import com.cheeseocean.im.postmaster.sender.MessageProducer;
import com.cheeseocean.im.postmaster.service.ConversationSeqService;
import com.cheeseocean.im.postmaster.service.GroupFanoutPlanner;
import com.cheeseocean.im.postmaster.service.GroupMembershipFacade;
import com.cheeseocean.im.postmaster.service.MessagePolicyEngine;
import com.cheeseocean.im.postmaster.service.MessageRouteDecision;
import org.apache.dubbo.config.annotation.DubboReference;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class IngressEventListener {

    private static final Logger                 log = CommonLoggers.POSTMASTER;
    private final        MessageProducer        messageProducer;
    private final        HistoryEventProducer   historyEventProducer;
    private final        GroupMembershipFacade  groupMembershipFacade;
    private final        ConversationSeqService conversationSeqService;
    private final        MessagePolicyEngine    messagePolicyEngine;
    private final        GroupFanoutPlanner    groupFanoutPlanner;
    private final        ConversationStateStore conversationStateStore;
    private final        UserMaxSeqPersistenceWriter userMaxSeqPersistenceWriter;
    @DubboReference(check = false, retries = 0)
    private              ConversationService    conversationService;

    public IngressEventListener(MessageProducer messageProducer,
                                HistoryEventProducer historyEventProducer,
                                GroupMembershipFacade groupMembershipFacade,
                                ConversationSeqService conversationSeqService,
                                MessagePolicyEngine messagePolicyEngine,
                                GroupFanoutPlanner groupFanoutPlanner,
                                ConversationStateStore conversationStateStore,
                                UserMaxSeqPersistenceWriter userMaxSeqPersistenceWriter) {
        this.messageProducer = messageProducer;
        this.historyEventProducer = historyEventProducer;
        this.groupMembershipFacade = groupMembershipFacade;
        this.conversationSeqService = conversationSeqService;
        this.messagePolicyEngine = messagePolicyEngine;
        this.groupFanoutPlanner = groupFanoutPlanner;
        this.conversationStateStore = conversationStateStore;
        this.userMaxSeqPersistenceWriter = userMaxSeqPersistenceWriter;
    }

    // 包级可见，供测试注入 ConversationService（生产路径由 @DubboReference 注入字段）
    IngressEventListener(MessageProducer messageProducer,
                         HistoryEventProducer historyEventProducer,
                         GroupMembershipFacade groupMembershipFacade,
                         ConversationSeqService conversationSeqService,
                         MessagePolicyEngine messagePolicyEngine,
                         GroupFanoutPlanner groupFanoutPlanner,
                         ConversationService conversationService) {
        this(messageProducer, historyEventProducer, groupMembershipFacade,
                conversationSeqService, messagePolicyEngine, groupFanoutPlanner, null, null);
        this.conversationService = conversationService;
    }

    // 消费 INGRESS 队列，批量接收同一会话的消息
    @QueueListener(topic = TopicNames.INGRESS, group = "postmaster-ingress", concurrency = 1, batch = true, batchSize = 500)
    public void onMessage(List<Message> msgs) {
        long started = ImMetrics.startTimer();
        try {
            handle(msgs);
            ImMetrics.ingressBatch(true, msgs == null ? 0 : msgs.size(), started);
        } catch (RuntimeException exception) {
            ImMetrics.ingressBatch(false, msgs == null ? 0 : msgs.size(), started);
            throw exception;
        }
    }

    // 包级可见，供测试直接调用。
    // 批次按 ConversationIdUtil.buildQueueKey 分组（single 和 notification 共享同一 key），
    // 同一批次可同时含 regular 和 notification 消息，
    // 二者路由到各自独立的处理方法，各自在方法内计算 conversationId。
    void handle(List<Message> msgs) {
        if (msgs == null || msgs.isEmpty()) return;

        // 普通消息 READ_RECEIPT 已废弃；已读只能走 typed CHAT_READ。
        List<Message> acceptedMessages = msgs.stream()
                .filter(msg -> msg != null && msg.getContentType() != ContentType.READ_RECEIPT)
                .toList();
        if (acceptedMessages.size() != msgs.size()) {
            log.warn("Discarded legacy READ_RECEIPT ingress messages, discarded={}",
                    msgs.size() - acceptedMessages.size());
        }
        if (acceptedMessages.isEmpty()) {
            return;
        }

        Message sample             = acceptedMessages.get(0);
        String  convId             = ConversationIdUtil.buildConversationId(sample);
        String  notificationConvId = ConversationIdUtil.buildNotificationConversationId(sample);

        // 二路分类
        List<EventCtx> storageList   = new ArrayList<>();
        List<EventCtx> transientList = new ArrayList<>();
        for (Message msg : acceptedMessages) {
            MessageRouteDecision d = messagePolicyEngine.decide(msg);
            (d.persistHistory() ? storageList : transientList).add(new EventCtx(msg, d.notification() ? notificationConvId : convId, d));
        }

        handleMessage(storageList, transientList);
    }

    private void handleMessage(List<EventCtx> storageList, List<EventCtx> transientList) {
        if (storageList.isEmpty() && transientList.isEmpty()) return;

        // 瞬时消息：输入中, 无需存储的通知等
        pushTransient(transientList);

        // 持久化消息：单聊、群聊、需保存的通知消息（群公告、拍一拍、群成员加入等）
        List<EventCtx> storageMsgList          = new ArrayList<>();
        List<EventCtx> storageNotificationList = new ArrayList<>();
        for (EventCtx storageMsg : storageList) {
            (storageMsg.decision().notification() ? storageNotificationList : storageMsgList).add(storageMsg);
        }

        ConversationSeqService.SeqBatch seqBatch             = null;
        ConversationSeqService.SeqBatch notificationSeqBatch = null;

        // 处理普通消息(单聊、群聊)
        if (!storageMsgList.isEmpty()) {
            // 持久化消息需分配序列号（会话严格递增）
            EventCtx msgSample = storageMsgList.get(0);
            seqBatch = conversationSeqService.allocateBatch(msgSample.convId(), storageMsgList.size());
            bindSeqs(storageMsgList, seqBatch.range().startInclusive());
            long currentMaxSeq = seqBatch.range().endInclusive();
            updateDirectUserState(storageMsgList, currentMaxSeq);
            if (conversationStateStore != null) {
                conversationStateStore.setConversationMaxSeq(msgSample.convId(), currentMaxSeq);
            }
            // 首次会话需为用户创建会话状态
            createConversationIfNeeded(msgSample.msg(), msgSample.convId(), seqBatch.isNewConversation());
        }

        // 处理通知消息
        if (!storageNotificationList.isEmpty()) {
            EventCtx notificationSample = storageNotificationList.get(0);
            notificationSeqBatch =
                    conversationSeqService.allocateBatch(notificationSample.convId(), storageNotificationList.size());
            bindSeqs(storageNotificationList, notificationSeqBatch.range().startInclusive());
        }


        // fanout: ingress -> history; ingress -> online_push
        // 持久化先入队列
        publishHistoryEvent(storageMsgList, seqBatch);
        publishHistoryEvent(storageNotificationList, notificationSeqBatch);

        // 在线推送：按批次聚合，群聊同一 groupId 只查询一次群类型和成员。
        List<KeyedMessage<Message>> directDeliveries = new ArrayList<>();
        Map<String, List<Message>> groupDeliveries = new LinkedHashMap<>();
        for (EventCtx p : storageList) {
            if (!p.decision().sendDelivery()) {
                continue;
            }
            if (p.msg().getChatType() == ChatType.GROUP) {
                String groupId = p.msg().getGroupId();
                groupDeliveries.computeIfAbsent(groupId == null ? "" : groupId,
                        ignored -> new ArrayList<>()).add(p.msg());
            } else {
                directDeliveries.add(new KeyedMessage<>(p.convId(), p.msg()));
            }
        }
        messageProducer.publishBatch(directDeliveries);
        groupDeliveries.forEach(this::fanoutGroupDeliveryBatch);
    }

    /**
     * 群消息扩散投递。
     *
     * <ul>
     *   <li>{@link GroupTypeEnum#NORMAL_GROUP}：写扩散——查询群成员，按 {@link GroupFanoutPlanner#partition}
     *       切片后，逐成员 publish 一个 keyed DeliveryEvent（{@code g:{groupId}:{memberId}}），
     *       postman 收到后按 {@code receiverId} 直投。</li>
     *   <li>{@link GroupTypeEnum#SUPER_GROUP}：读扩散——不投递，仅持久化即可，客户端按 seq 同步。</li>
     *   <li>{@code null}：按 NORMAL_GROUP 写扩散兜底，兼容未返回群类型的旧 provider。</li>
     * </ul>
     *
     * <p>群类型或成员查询异常必须上抛给队列容器，由队列重试/DLT 处理。不能把依赖故障降级成
     * “无投递”，否则普通群成员不会收到实时消息，且消费位点仍会推进。
     */
    private void fanoutGroupDeliveryBatch(String groupId, List<Message> groupMessages) {
        Message sample = groupMessages == null || groupMessages.isEmpty() ? null : groupMessages.get(0);
        if (groupId == null || groupId.isBlank()) {
            log.warn("Group delivery skipped: groupId is missing, serverMsgId={}",
                    sample == null ? null : sample.getServerMsgId());
            return;
        }
        GroupTypeEnum groupType = groupMembershipFacade.loadGroupType(groupId);
        if (groupType == GroupTypeEnum.SUPER_GROUP) {
            // 读扩散：仅持久化，客户端按 seq 拉取。无投递事件 publish。
            log.debug("Group messages sent in read-fanout mode (SUPER_GROUP): groupId={}, messages={}",
                    groupId, groupMessages.size());
            return;
        }
        // null 或 NORMAL_GROUP：写扩散
        List<String> members = groupMembershipFacade.loadGroupMembers(groupId);
        if (members == null || members.isEmpty()) {
            log.warn("Group has no members to fan out: groupId={}, serverMsgId={}", groupId,
                    sample == null ? null : sample.getServerMsgId());
            return;
        }
        List<List<String>> batches = groupFanoutPlanner.partition(members);
        for (List<String> batch : batches) {
            List<KeyedMessage<String>> targets = new ArrayList<>(batch.size());
            for (String memberId : batch) {
                targets.add(new KeyedMessage<>(groupFanoutPlanner.deliveryKey(groupId, memberId), memberId));
            }
            messageProducer.publishForTargets(groupMessages, targets);
            for (String memberId : batch) {
                advanceUserState(memberId, "g:" + groupId, groupMessages.get(groupMessages.size() - 1).getSeq(),
                        !memberId.equals(sample.getSenderId()));
            }
        }
        log.debug("Group messages fanned out: groupId={}, members={}, batches={}, messages={}",
                groupId, members.size(), batches.size(), groupMessages.size());
    }

    private void updateDirectUserState(List<EventCtx> messages, long maxSeq) {
        if (conversationStateStore == null || userMaxSeqPersistenceWriter == null) return;
        for (EventCtx ctx : messages) {
            Message message = ctx.msg();
            if (message.getChatType() == ChatType.GROUP) continue;
            advanceUserState(message.getSenderId(), ctx.convId(), maxSeq, false);
            if (message.getReceiverId() != null && !message.getReceiverId().equals(message.getSenderId())) {
                advanceUserState(message.getReceiverId(), ctx.convId(), maxSeq, true);
            }
        }
    }

    private void advanceUserState(String userId, String conversationId, long maxSeq, boolean countUnread) {
        if (conversationStateStore == null || userMaxSeqPersistenceWriter == null
                || userId == null || userId.isBlank()) return;
        conversationStateStore.advanceUserMaxSeq(userId, conversationId, maxSeq, countUnread);
        userMaxSeqPersistenceWriter.enqueue(userId, conversationId, maxSeq);
    }

    // ── 共用私有方法 ──────────────────────────────────────────────────────────

    private void pushTransient(List<EventCtx> transientList) {
        List<KeyedMessage<Message>> deliveries = new ArrayList<>();
        for (EventCtx ctx : transientList) {
            if (!ctx.decision().sendDelivery()) continue;
            deliveries.add(new KeyedMessage<>(ctx.convId, ctx.msg()));
        }
        messageProducer.publishBatch(deliveries);
    }

    private void bindSeqs(List<EventCtx> ctxList, long startSeq) {
        long seq = startSeq;
        for (EventCtx ctx : ctxList) {
            ctx.msg.setSeq(seq++);
        }
    }

    private void publishHistoryEvent(List<EventCtx> ctxList, ConversationSeqService.SeqBatch seqBatch) {
        if (ctxList == null || ctxList.isEmpty() || seqBatch == null) {
            return;
        }
        HistoryEvent historyEvent = new HistoryEvent();
        historyEvent.setConversationId(ctxList.get(0).convId());
        historyEvent.setLastMaxSeq(seqBatch.lastMaxSeq());
        historyEvent.setBeginSeq(seqBatch.range().startInclusive());
        historyEvent.setEndSeq(seqBatch.range().endInclusive());
        historyEvent.setMessages(ctxList.stream().map(EventCtx::msg).collect(Collectors.toList()));
        historyEventProducer.publish(historyEvent.getConversationId(), historyEvent);
    }

    private void createConversationIfNeeded(Message sample, String conversationId, boolean newConversation) {
        if (!newConversation) {
            return;
        }
        if (sample.getChatType() != null && sample.getChatType() == ChatType.GROUP) {
            List<String> userIds = groupMembershipFacade.loadGroupMembers(sample.getGroupId());
            conversationService.createGroupChatConversations(sample.getGroupId(), conversationId, userIds);
            return;
        }
        conversationService.createSingleChatConversation(
                sample.getSenderId(),
                sample.getReceiverId(),
                conversationId,
                sample.getChatType() == null ? 0 : sample.getChatType().getCode()
        );
    }

    /**
     * 路由决策结果与原始事件的绑定，避免对同一条消息重复调用 decide()
     */
    private record EventCtx(Message msg, String convId, MessageRouteDecision decision) {
    }


}
