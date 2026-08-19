package com.cheeseocean.im.postmaster.listener;

import com.cheeseocean.im.common.api.conversation.ConversationService;
import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.event.GroupFanoutEvent;
import com.cheeseocean.im.common.api.enums.ContentType;
import com.cheeseocean.im.common.api.enums.ChatType;
import com.cheeseocean.im.common.api.enums.GroupTypeEnum;
import com.cheeseocean.im.common.api.event.HistoryEvent;
import com.cheeseocean.im.common.api.permission.GroupMessageSendPermissionDecision;
import com.cheeseocean.im.common.api.permission.GroupMessageSendPermissionResult;
import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.common.core.constants.TopicNames;
import com.cheeseocean.im.common.core.logging.CommonLoggers;
import com.cheeseocean.im.common.core.metrics.ImMetrics;
import com.cheeseocean.im.common.core.queue.annotation.QueueListener;
import com.cheeseocean.im.common.core.queue.KeyedMessage;
import com.cheeseocean.im.common.core.store.conversation.ConversationStateStore;
import com.cheeseocean.im.common.core.store.idempotency.ingress.IngressMessageInboxStore;
import com.cheeseocean.im.common.core.util.ConversationIdUtil;
import com.cheeseocean.im.common.core.util.IdGenerator;
import com.cheeseocean.im.postmaster.sender.HistoryEventProducer;
import com.cheeseocean.im.postmaster.sender.MessageProducer;
import com.cheeseocean.im.postmaster.service.ConversationSeqService;
import com.cheeseocean.im.postmaster.service.GroupFanoutPlanner;
import com.cheeseocean.im.postmaster.service.GroupMembershipFacade;
import com.cheeseocean.im.postmaster.service.MessagePolicyEngine;
import com.cheeseocean.im.postmaster.service.MessageRouteDecision;
import com.cheeseocean.im.postmaster.service.UserMaxSeqPersistenceWriter;
import org.apache.dubbo.config.annotation.DubboReference;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final        IngressMessageInboxStore ingressMessageInboxStore;
    @DubboReference(check = false, retries = 0)
    private              ConversationService    conversationService;

    @org.springframework.beans.factory.annotation.Autowired
    public IngressEventListener(MessageProducer messageProducer,
                                HistoryEventProducer historyEventProducer,
                                GroupMembershipFacade groupMembershipFacade,
                                ConversationSeqService conversationSeqService,
                                MessagePolicyEngine messagePolicyEngine,
                                GroupFanoutPlanner groupFanoutPlanner,
                                ConversationStateStore conversationStateStore,
                                UserMaxSeqPersistenceWriter userMaxSeqPersistenceWriter,
                                IngressMessageInboxStore ingressMessageInboxStore) {
        this.messageProducer = messageProducer;
        this.historyEventProducer = historyEventProducer;
        this.groupMembershipFacade = groupMembershipFacade;
        this.conversationSeqService = conversationSeqService;
        this.messagePolicyEngine = messagePolicyEngine;
        this.groupFanoutPlanner = groupFanoutPlanner;
        this.conversationStateStore = conversationStateStore;
        this.userMaxSeqPersistenceWriter = userMaxSeqPersistenceWriter;
        this.ingressMessageInboxStore = ingressMessageInboxStore;
    }

    // 包级可见，供测试注入 ConversationService（生产路径由 @DubboReference 注入字段）
    IngressEventListener(MessageProducer messageProducer,
                         HistoryEventProducer historyEventProducer,
                         GroupMembershipFacade groupMembershipFacade,
                         ConversationSeqService conversationSeqService,
                         MessagePolicyEngine messagePolicyEngine,
                         GroupFanoutPlanner groupFanoutPlanner,
                         IngressMessageInboxStore ingressMessageInboxStore,
                         ConversationService conversationService) {
        this(messageProducer, historyEventProducer, groupMembershipFacade,
                conversationSeqService, messagePolicyEngine, groupFanoutPlanner, null, null,
                ingressMessageInboxStore);
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

        Map<String, Message> uniqueMessages = uniqueMessages(acceptedMessages);
        String ownerToken = IdGenerator.generateUUID();
        List<IngressMessageInboxStore.Claim> claims = acquireClaims(uniqueMessages, ownerToken);
        Map<String, IngressMessageInboxStore.Claim> claimByKey = claims.stream()
                .collect(Collectors.toMap(
                        IngressMessageInboxStore.Claim::key,
                        claim -> claim,
                        (left, right) -> left,
                        LinkedHashMap::new));
        List<Message> processingMessages = uniqueMessages.entrySet().stream()
                .filter(entry -> claimByKey.get(entry.getKey()).status()
                        == IngressMessageInboxStore.ClaimStatus.ACQUIRED)
                .map(Map.Entry::getValue)
                .toList();
        if (processingMessages.isEmpty()) {
            return;
        }
        List<String> processingKeys = processingMessages.stream()
                .map(this::inboxKey)
                .toList();
        try {
            Map<String, GroupMessageSendPermissionResult> groupPermissions =
                    validateGroupSendPermissions(processingMessages);
            handleClaimed(processingMessages, claimByKey, groupPermissions, ownerToken);
            ingressMessageInboxStore.completeBatch(processingKeys, ownerToken);
        } catch (RuntimeException exception) {
            try {
                ingressMessageInboxStore.releaseBatch(processingKeys, ownerToken);
            } catch (RuntimeException releaseFailure) {
                exception.addSuppressed(releaseFailure);
            }
            throw exception;
        }
    }

    private void handleClaimed(List<Message> acceptedMessages,
                               Map<String, IngressMessageInboxStore.Claim> claimByKey,
                               Map<String, GroupMessageSendPermissionResult> groupPermissions,
                               String ownerToken) {
        Message sample             = acceptedMessages.get(0);
        String  convId             = ConversationIdUtil.buildConversationId(sample);
        String  notificationConvId = ConversationIdUtil.buildNotificationConversationId(sample);

        // 二路分类
        List<EventCtx> storageList   = new ArrayList<>();
        List<EventCtx> transientList = new ArrayList<>();
        for (Message msg : acceptedMessages) {
            MessageRouteDecision d = messagePolicyEngine.decide(msg);
            String key = inboxKey(msg);
            IngressMessageInboxStore.Claim claim = claimByKey.get(key);
            (d.persistHistory() ? storageList : transientList).add(new EventCtx(
                    msg,
                    d.notification() ? notificationConvId : convId,
                    d,
                    key,
                    claim.assignedSeq()));
        }

        handleMessage(storageList, transientList, groupPermissions, ownerToken);
    }

    private void handleMessage(List<EventCtx> storageList,
                               List<EventCtx> transientList,
                               Map<String, GroupMessageSendPermissionResult> groupPermissions,
                               String ownerToken) {
        if (storageList.isEmpty() && transientList.isEmpty()) return;

        // 瞬时消息：输入中, 无需存储的通知等
        pushTransient(transientList);

        // 持久化消息：单聊、群聊、需保存的通知消息（群公告、拍一拍、群成员加入等）
        List<EventCtx> storageMsgList          = new ArrayList<>();
        List<EventCtx> storageNotificationList = new ArrayList<>();
        for (EventCtx storageMsg : storageList) {
            (storageMsg.decision().notification() ? storageNotificationList : storageMsgList).add(storageMsg);
        }

        SeqAssignmentResult seqBatch             = null;
        SeqAssignmentResult notificationSeqBatch = null;

        // 处理普通消息(单聊、群聊)
        if (!storageMsgList.isEmpty()) {
            // 持久化消息需分配序列号（会话严格递增）
            EventCtx msgSample = storageMsgList.get(0);
            seqBatch = bindStableSeqs(storageMsgList, ownerToken);
            long currentMaxSeq = seqBatch.endSeq();
            updateDirectUserState(storageMsgList, currentMaxSeq);
            if (conversationStateStore != null) {
                conversationStateStore.setConversationMaxSeq(msgSample.convId(), currentMaxSeq);
            }
            // 首次会话需为用户创建会话状态
            createConversationIfNeeded(
                    msgSample.msg(),
                    msgSample.convId(),
                    seqBatch.newConversation(),
                    groupPermissions);
        }

        // 处理通知消息
        if (!storageNotificationList.isEmpty()) {
            notificationSeqBatch = bindStableSeqs(storageNotificationList, ownerToken);
        }


        // fanout: ingress -> history; ingress -> online_push
        // 持久化先入队列
        publishHistoryEvent(storageMsgList, seqBatch);
        publishHistoryEvent(storageNotificationList, notificationSeqBatch);

        // 在线推送：按批次聚合，群聊同一 groupId 只查询一次群类型和成员。
        List<KeyedMessage<Message>> directDeliveries = new ArrayList<>();
        Map<String, List<Message>> groupDeliveries = new LinkedHashMap<>();
        List<EventCtx> orderedStorage = new ArrayList<>(storageList.size());
        orderedStorage.addAll(storageMsgList);
        orderedStorage.addAll(storageNotificationList);
        for (EventCtx p : orderedStorage) {
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
        boolean createGroupConversation = (seqBatch != null && seqBatch.newConversation())
                || (notificationSeqBatch != null && notificationSeqBatch.newConversation());
        groupDeliveries.forEach((groupId, messages) -> {
            GroupMessageSendPermissionResult permission =
                    requireGroupPermission(groupPermissions, groupId);
            publishGroupFanoutJob(
                    groupId,
                    messages,
                    permission.getGroupType(),
                    permission.getMembershipVersion(),
                    createGroupConversation);
        });
    }

    /**
     * 群消息扩散任务发布。
     *
     * <ul>
     *   <li>{@link GroupTypeEnum#NORMAL_GROUP}：发布紧凑任务，成员查询和切片由独立 worker 完成。</li>
     *   <li>{@link GroupTypeEnum#SUPER_GROUP}：读扩散——不投递，仅持久化即可，客户端按 seq 同步。</li>
     * </ul>
     *
     * <p>任务发布必须取得 broker ACK；成员依赖故障只重试 fanout consumer，不再占用 ingress。
     */
    private void publishGroupFanoutJob(String groupId,
                                       List<Message> groupMessages,
                                       GroupTypeEnum groupType,
                                       long membershipVersion,
                                       boolean createConversation) {
        Message sample = groupMessages == null || groupMessages.isEmpty() ? null : groupMessages.get(0);
        if (groupId == null || groupId.isBlank()) {
            log.warn("Group delivery skipped: groupId is missing, serverMsgId={}",
                    sample == null ? null : sample.getServerMsgId());
            return;
        }
        if (groupType == GroupTypeEnum.SUPER_GROUP) {
            // 读扩散：仅持久化，客户端按 seq 拉取。无投递事件 publish。
            log.debug("Group messages sent in read-fanout mode (SUPER_GROUP): groupId={}, messages={}",
                    groupId, groupMessages.size());
            return;
        }
        if (membershipVersion <= 0L) {
            throw new IllegalStateException(
                    "Group membership epoch baseline is unavailable: groupId=" + groupId);
        }
        for (List<Message> jobMessages : groupFanoutPlanner.partitionMessages(groupMessages)) {
            publishGroupFanoutJobChunk(
                    groupId,
                    jobMessages,
                    membershipVersion,
                    createConversation);
        }
    }

    private void publishGroupFanoutJobChunk(String groupId,
                                            List<Message> groupMessages,
                                            long membershipVersion,
                                            boolean createConversation) {
        GroupFanoutEvent event = new GroupFanoutEvent();
        event.setJobId(groupFanoutJobId(groupId, groupMessages));
        event.setGroupId(groupId);
        event.setConversationId("g:" + groupId);
        // newConversation 是首次分配时的瞬时结果；seq=1 是 inbox 重放后仍稳定的首会话事实。
        event.setCreateConversation(createConversation
                || groupMessages.stream().anyMatch(message -> message != null && message.getSeq() == 1L));
        event.setMembershipVersion(membershipVersion);
        event.setMessages(groupMessages);
        messageProducer.publishGroupFanout(groupFanoutPlanner.fanoutKey(groupId), event);
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

    private SeqAssignmentResult bindStableSeqs(List<EventCtx> messages, String ownerToken) {
        List<EventCtx> missing = messages.stream()
                .filter(ctx -> ctx.assignedSeq() <= 0L)
                .toList();
        ConversationSeqService.SeqBatch allocated = null;
        if (!missing.isEmpty()) {
            allocated = conversationSeqService.allocateBatch(missing.get(0).convId(), missing.size());
            long proposedSeq = allocated.range().startInclusive();
            List<IngressMessageInboxStore.SequenceBinding> bindings = new ArrayList<>(missing.size());
            for (EventCtx ctx : missing) {
                bindings.add(new IngressMessageInboxStore.SequenceBinding(ctx.inboxKey(), proposedSeq++));
            }
            Map<String, Long> stableSequences = ingressMessageInboxStore.bindSequences(
                    bindings,
                    ownerToken);
            for (EventCtx ctx : missing) {
                Long stableSeq = stableSequences.get(ctx.inboxKey());
                if (stableSeq == null || stableSeq <= 0L) {
                    throw new IllegalStateException("Ingress inbox did not return stable seq");
                }
                ctx.msg().setSeq(stableSeq);
            }
        }
        for (EventCtx ctx : messages) {
            if (ctx.assignedSeq() > 0L) {
                ctx.msg().setSeq(ctx.assignedSeq());
            }
        }
        messages.sort(java.util.Comparator.comparingLong(ctx -> ctx.msg().getSeq()));
        long beginSeq = messages.stream().mapToLong(ctx -> ctx.msg().getSeq()).min().orElseThrow();
        long endSeq = messages.stream().mapToLong(ctx -> ctx.msg().getSeq()).max().orElseThrow();
        long lastMaxSeq = allocated == null ? Math.max(0L, beginSeq - 1L) : allocated.lastMaxSeq();
        boolean newConversation = messages.stream().anyMatch(ctx -> ctx.msg().getSeq() == 1L);
        return new SeqAssignmentResult(beginSeq, endSeq, lastMaxSeq, newConversation);
    }

    private Map<String, Message> uniqueMessages(List<Message> messages) {
        Map<String, Message> unique = new LinkedHashMap<>();
        Map<String, String> fingerprints = new LinkedHashMap<>();
        for (Message message : messages) {
            if (message.getServerMsgId() == null || message.getServerMsgId().isBlank()) {
                throw new IllegalArgumentException("Ingress message requires serverMsgId");
            }
            String key = inboxKey(message);
            String fingerprint = IngressMessageFingerprint.payload(message);
            String previous = fingerprints.putIfAbsent(key, fingerprint);
            if (previous != null && !previous.equals(fingerprint)) {
                throw new IllegalStateException("Duplicate serverMsgId carries conflicting ingress payload");
            }
            unique.putIfAbsent(key, message);
        }
        return unique;
    }

    private Map<String, GroupMessageSendPermissionResult> validateGroupSendPermissions(
            List<Message> messages) {
        Map<String, List<String>> sendersByGroup = new LinkedHashMap<>();
        for (Message message : messages) {
            if (message.getChatType() != ChatType.GROUP) {
                continue;
            }
            if (message.getGroupId() == null || message.getGroupId().isBlank()
                    || message.getSenderId() == null || message.getSenderId().isBlank()) {
                throw new IllegalArgumentException("Group ingress requires groupId and senderId");
            }
            sendersByGroup.computeIfAbsent(message.getGroupId(), ignored -> new ArrayList<>())
                    .add(message.getSenderId());
        }
        Map<String, GroupMessageSendPermissionResult> results = new LinkedHashMap<>();
        sendersByGroup.forEach((groupId, senderIds) -> {
            List<String> uniqueSenderIds = senderIds.stream().distinct().toList();
            GroupMessageSendPermissionResult result =
                    groupMembershipFacade.checkSendPermissions(groupId, uniqueSenderIds);
            if (result == null || result.getGroupType() == null) {
                throw new IllegalStateException("Group send permission provider returned incomplete result");
            }
            for (String senderId : uniqueSenderIds) {
                GroupMessageSendPermissionDecision decision = result.decisionFor(senderId);
                if (decision == null || !decision.isAllowed()) {
                    throw new IllegalStateException("Group ingress sender is not allowed: groupId="
                            + groupId + ", senderId=" + senderId + ", permission="
                            + (decision == null ? "MISSING" : decision.permission().name()));
                }
            }
            results.put(groupId, result);
        });
        return results;
    }

    private List<IngressMessageInboxStore.Claim> acquireClaims(Map<String, Message> messages,
                                                               String ownerToken) {
        List<IngressMessageInboxStore.ClaimRequest> requests = messages.entrySet().stream()
                .map(entry -> new IngressMessageInboxStore.ClaimRequest(
                        entry.getKey(),
                        IngressMessageFingerprint.payload(entry.getValue())))
                .toList();
        while (true) {
            long now = System.currentTimeMillis();
            List<IngressMessageInboxStore.Claim> claims =
                    ingressMessageInboxStore.claimBatch(requests, ownerToken, now);
            if (claims.size() != requests.size()) {
                throw new IllegalStateException("Ingress inbox returned incomplete claim batch");
            }
            if (claims.stream().anyMatch(claim ->
                    claim.status() == IngressMessageInboxStore.ClaimStatus.CONFLICT)) {
                throw new IllegalStateException("Stable serverMsgId carries conflicting ingress payload");
            }
            long earliestLease = claims.stream()
                    .filter(claim -> claim.status() == IngressMessageInboxStore.ClaimStatus.IN_PROGRESS)
                    .mapToLong(IngressMessageInboxStore.Claim::leaseUntil)
                    .min()
                    .orElse(0L);
            if (earliestLease == 0L) {
                return claims;
            }
            List<String> acquiredKeys = claims.stream()
                    .filter(claim -> claim.status() == IngressMessageInboxStore.ClaimStatus.ACQUIRED)
                    .map(IngressMessageInboxStore.Claim::key)
                    .toList();
            ingressMessageInboxStore.releaseBatch(acquiredKeys, ownerToken);
            waitForLease(earliestLease - now);
        }
    }

    private void waitForLease(long remainingMillis) {
        try {
            Thread.sleep(Math.max(1L, Math.min(1_000L, remainingMillis)));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for ingress inbox lease", exception);
        }
    }

    private String inboxKey(Message message) {
        return RedisKeys.ingressMessageInbox(
                IngressMessageFingerprint.serverMessageId(message.getServerMsgId()));
    }

    private void publishHistoryEvent(List<EventCtx> ctxList, SeqAssignmentResult seqBatch) {
        if (ctxList == null || ctxList.isEmpty() || seqBatch == null) {
            return;
        }
        HistoryEvent historyEvent = new HistoryEvent();
        historyEvent.setConversationId(ctxList.get(0).convId());
        historyEvent.setLastMaxSeq(seqBatch.lastMaxSeq());
        historyEvent.setBeginSeq(seqBatch.beginSeq());
        historyEvent.setEndSeq(seqBatch.endSeq());
        historyEvent.setMessages(ctxList.stream().map(EventCtx::msg).collect(Collectors.toList()));
        historyEventProducer.publish(historyEvent.getConversationId(), historyEvent);
    }

    private void createConversationIfNeeded(
            Message sample,
            String conversationId,
            boolean newConversation,
            Map<String, GroupMessageSendPermissionResult> groupPermissions) {
        if (!newConversation) {
            return;
        }
        if (sample.getChatType() != null && sample.getChatType() == ChatType.GROUP) {
            GroupTypeEnum groupType = requireGroupPermission(
                    groupPermissions, sample.getGroupId()).getGroupType();
            if (groupType == GroupTypeEnum.SUPER_GROUP) {
                // 超级群走读扩散，不能在首条消息时枚举全量成员创建用户会话。
                return;
            }
            // 普通群成员枚举与首会话创建由 GROUP_FANOUT worker 完成，避免阻塞 ingress。
            return;
        }
        conversationService.createSingleChatConversation(
                sample.getSenderId(),
                sample.getReceiverId(),
                conversationId,
                sample.getChatType() == null ? 0 : sample.getChatType().getCode()
        );
    }

    private GroupMessageSendPermissionResult requireGroupPermission(
            Map<String, GroupMessageSendPermissionResult> permissions,
            String groupId) {
        GroupMessageSendPermissionResult result = permissions.get(groupId);
        if (result == null || result.getGroupType() == null) {
            throw new IllegalStateException("Missing validated group send context: " + groupId);
        }
        return result;
    }

    private String groupFanoutJobId(String groupId, List<Message> messages) {
        try {
            StringBuilder identity = new StringBuilder()
                    .append(groupId.length()).append(':').append(groupId);
            for (Message message : messages) {
                String serverMsgId = message == null ? "" : java.util.Objects.toString(message.getServerMsgId(), "");
                identity.append('|').append(serverMsgId.length()).append(':').append(serverMsgId);
            }
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(identity.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    /**
     * 路由决策结果与原始事件的绑定，避免对同一条消息重复调用 decide()
     */
    private record EventCtx(Message msg,
                            String convId,
                            MessageRouteDecision decision,
                            String inboxKey,
                            long assignedSeq) {
    }

    private record SeqAssignmentResult(long beginSeq,
                                       long endSeq,
                                       long lastMaxSeq,
                                       boolean newConversation) {
    }


}
