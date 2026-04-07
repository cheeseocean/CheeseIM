package com.cheeseocean.im.postmaster.listener;

import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.enums.ContentType;
import com.cheeseocean.im.common.api.enums.SessionType;
import com.cheeseocean.im.common.api.event.HistoryEvent;
import com.cheeseocean.im.common.core.constants.TopicNames;
import com.cheeseocean.im.common.core.logging.CommonLoggers;
import com.cheeseocean.im.common.core.queue.annotation.QueueListener;
import com.cheeseocean.im.common.core.util.ConversationIdUtil;
import com.cheeseocean.im.postmaster.sender.HistoryEventProducer;
import com.cheeseocean.im.postmaster.sender.MessageProducer;
import com.cheeseocean.im.postmaster.service.ConversationSeqService;
import com.cheeseocean.im.postmaster.service.ConversationWriteFacade;
import com.cheeseocean.im.postmaster.service.GroupMembershipFacade;
import com.cheeseocean.im.postmaster.service.MessagePolicyEngine;
import com.cheeseocean.im.postmaster.service.MessageRouteDecision;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class IngressEventListener {

    private static final Logger                  log = CommonLoggers.POSTMASTER;
    private final        MessageProducer         messageProducer;
    private final        HistoryEventProducer    historyEventProducer;
    private final        GroupMembershipFacade   groupMembershipFacade;
    private final        ConversationSeqService  conversationSeqService;
    private final        MessagePolicyEngine     messagePolicyEngine;
    private final        ConversationWriteFacade conversationWriteService;

    public IngressEventListener(MessageProducer messageProducer,
                                HistoryEventProducer historyEventProducer,
                                GroupMembershipFacade groupMembershipFacade,
                                ConversationSeqService conversationSeqService,
                                MessagePolicyEngine messagePolicyEngine,
                                ConversationWriteFacade conversationWriteService) {
        this.messageProducer = messageProducer;
        this.historyEventProducer = historyEventProducer;
        this.groupMembershipFacade = groupMembershipFacade;
        this.conversationSeqService = conversationSeqService;
        this.messagePolicyEngine = messagePolicyEngine;
        this.conversationWriteService = conversationWriteService;
    }

    // 消费 INGRESS 队列，批量接收同一会话的消息
    @QueueListener(topic = TopicNames.INGRESS, group = "postman-ingress", concurrency = 1, batch = true, batchSize = 500)
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
            EventCtx notificationSample = storageMsgList.get(0);
            notificationSeqBatch =
                    conversationSeqService.allocateBatch(notificationSample.convId(), storageNotificationList.size());
            bindSeqs(storageNotificationList, seqBatch.range().startInclusive());
        }


        // fanout: ingress -> history; ingress -> online_push
        // 持久化先入队列
        publishHistoryEvent(storageMsgList, seqBatch);
        publishHistoryEvent(storageNotificationList, notificationSeqBatch);

        // 在线推送
        for (EventCtx p : storageList) {
            if (p.decision().sendDelivery()) messageProducer.publish(p.convId(), p.msg());
        }
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
        for (EventCtx ctx : transientList) {
            if (!ctx.decision().sendDelivery()) continue;
            messageProducer.publish(ctx.convId, ctx.msg());
        }
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
        if (sample.getSessionType() != null && sample.getSessionType() == SessionType.GROUP) {
            List<String> userIds = groupMembershipFacade.loadGroupMembers(sample.getGroupId());
            conversationWriteService.createGroupChatConversations(sample.getGroupId(), conversationId, userIds);
            return;
        }
        conversationWriteService.createSingleChatConversation(
                sample.getSenderId(),
                sample.getReceiverId(),
                conversationId,
                sample.getSessionType() == null ? 0 : sample.getSessionType().getCode()
        );
    }

    /**
     * 路由决策结果与原始事件的绑定，避免对同一条消息重复调用 decide()
     */
    private record EventCtx(Message msg, String convId, MessageRouteDecision decision) {
    }


}
