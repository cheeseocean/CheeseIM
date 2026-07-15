package com.cheeseocean.im.postmaster.listener;

import com.cheeseocean.im.common.api.conversation.ConversationService;
import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.enums.ContentType;
import com.cheeseocean.im.common.api.enums.ChatType;
import com.cheeseocean.im.common.api.enums.GroupTypeEnum;
import com.cheeseocean.im.common.api.event.HistoryEvent;
import com.cheeseocean.im.common.core.constants.TopicNames;
import com.cheeseocean.im.common.core.logging.CommonLoggers;
import com.cheeseocean.im.common.core.queue.annotation.QueueListener;
import com.cheeseocean.im.common.core.queue.KeyedMessage;
import com.cheeseocean.im.common.core.util.ConversationIdUtil;
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
    @DubboReference(check = false, retries = 0)
    private              ConversationService    conversationService;

    public IngressEventListener(MessageProducer messageProducer,
                                HistoryEventProducer historyEventProducer,
                                GroupMembershipFacade groupMembershipFacade,
                                ConversationSeqService conversationSeqService,
                                MessagePolicyEngine messagePolicyEngine,
                                GroupFanoutPlanner groupFanoutPlanner) {
        this.messageProducer = messageProducer;
        this.historyEventProducer = historyEventProducer;
        this.groupMembershipFacade = groupMembershipFacade;
        this.conversationSeqService = conversationSeqService;
        this.messagePolicyEngine = messagePolicyEngine;
        this.groupFanoutPlanner = groupFanoutPlanner;
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
                conversationSeqService, messagePolicyEngine, groupFanoutPlanner);
        this.conversationService = conversationService;
    }

    // 消费 INGRESS 队列，批量接收同一会话的消息
    @QueueListener(topic = TopicNames.INGRESS, group = "postmaster-ingress", concurrency = 1, batch = true, batchSize = 500)
    public void onMessage(List<Message> msgs) {
        try {
            handle(msgs);
        } catch (Exception e) {
            log.error("处理 ingress 事件批次失败: {}", msgs, e);
        }
    }

    // 包级可见，供测试直接调用。
    // 批次按 ConversationIdUtil.buildQueueKey 分组（single 和 notification 共享同一 key），
    // 同一批次可同时含 regular 和 notification 消息，
    // 二者路由到各自独立的处理方法，各自在方法内计算 conversationId。
    void handle(List<Message> msgs) {
        if (msgs == null || msgs.isEmpty()) return;

        // 已读回执旁路：提前将已读 seq 写入 Redis，消息本身继续走完整管道
        preProcessReadReceipts(msgs);

        Message sample             = msgs.get(0);
        String  convId             = ConversationIdUtil.buildConversationId(sample);
        String  notificationConvId = ConversationIdUtil.buildNotificationConversationId(sample);

        // 二路分类
        List<EventCtx> storageList   = new ArrayList<>();
        List<EventCtx> transientList = new ArrayList<>();
        for (Message msg : msgs) {
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
     *   <li>{@code null}：群不存在或 Dubbo 异常，按 NORMAL_GROUP 写扩散兜底，避免安全降级丢失投递。</li>
     * </ul>
     *
     * <p>为何不在 ingress 吞下群投递失败：此处异常上抛会导致整批 ingress 重投，重复 seq 分配。
     * 因此本方法内捕获 Dubbo/查询异常并降级为"无投递"，仅在日志记录；客户端按 seq 同步自愈。
     * 单聊的原 per-message publish 等价语义不受影响。
     */
    private void fanoutGroupDeliveryBatch(String groupId, List<Message> groupMessages) {
        Message sample = groupMessages == null || groupMessages.isEmpty() ? null : groupMessages.get(0);
        if (groupId == null || groupId.isBlank()) {
            log.warn("Group delivery skipped: groupId is missing, serverMsgId={}",
                    sample == null ? null : sample.getServerMsgId());
            return;
        }
        GroupTypeEnum groupType;
        try {
            groupType = groupMembershipFacade.loadGroupType(groupId);
        } catch (Exception e) {
            // Dubbo 异常时不降级为读扩散——按 NORMAL_GROUP 兜底，至少保证普通群能投递
            log.warn("Load group type failed, fallback to NORMAL_GROUP write-fanout: groupId={}", groupId, e);
            groupType = GroupTypeEnum.NORMAL_GROUP;
        }
        if (groupType == GroupTypeEnum.SUPER_GROUP) {
            // 读扩散：仅持久化，客户端按 seq 拉取。无投递事件 publish。
            log.debug("Group messages sent in read-fanout mode (SUPER_GROUP): groupId={}, messages={}",
                    groupId, groupMessages.size());
            return;
        }
        // null 或 NORMAL_GROUP：写扩散
        List<String> members;
        try {
            members = groupMembershipFacade.loadGroupMembers(groupId);
        } catch (Exception e) {
            log.warn("Load group members failed, group delivery abandoned: groupId={}", groupId, e);
            return;
        }
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
        }
        log.debug("Group messages fanned out: groupId={}, members={}, batches={}, messages={}",
                groupId, members.size(), batches.size(), groupMessages.size());
    }

    // ── 共用私有方法 ──────────────────────────────────────────────────────────

    private void preProcessReadReceipts(List<Message> msgs) {
        List<Message> readReceipts = new ArrayList<>();
        for (Message msg : msgs) {
            if (msg.getContentType() != null
                    && msg.getContentType() == ContentType.READ_RECEIPT) {
                readReceipts.add(msg);
            }
        }
        if (!readReceipts.isEmpty()) {
//            messageStateService.processReadReceipts(readReceipts);
        }
    }

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
