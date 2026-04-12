package com.cheeseocean.im.postmaster.service;

import com.cheeseocean.im.common.api.dto.message.ConversationLastMessageSummary;
import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.dto.message.ReadReceiptPayload;
import com.cheeseocean.im.common.core.store.conversation.ConversationStateStore;
import com.cheeseocean.im.common.core.util.ConversationIdUtil;
import com.cheeseocean.im.common.core.util.MessagePreviewUtil;
import com.cheeseocean.im.postmaster.model.MessageWithTargets;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cheeseocean.im.common.core.logging.CommonLoggers;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.nio.charset.StandardCharsets;

@Service
public class MessageStateService {

    private static final Logger log = CommonLoggers.POSTMASTER;

    private final ConversationStateStore conversationStateStore;
    private final ObjectMapper objectMapper;
    private final UserMaxSeqPersistenceWriter userMaxSeqPersistenceWriter;

    public MessageStateService(ConversationStateStore conversationStateStore,
                               ObjectMapper objectMapper,
                               UserMaxSeqPersistenceWriter userMaxSeqPersistenceWriter) {
        this.conversationStateStore = conversationStateStore;
        this.objectMapper = objectMapper;
        this.userMaxSeqPersistenceWriter = userMaxSeqPersistenceWriter;
    }

    public void apply(Message message, List<String> targetUserIds) {
        String conversationId = resolveConversationId(message);
        if (message == null || conversationId == null || message.getSeq() == null) {
            return;
        }

        MessageRouteDecision decision = new DefaultMessagePolicyEngine().decide(message);
        if (!decision.updateConversation() && !decision.updateUnread() && !decision.updateLastMessage()) {
            return;
        }

        conversationStateStore.setConversationMinSeqIfAbsent(conversationId, message.getSeq());
        conversationStateStore.setConversationMaxSeq(conversationId, message.getSeq());

        Set<String> participants = normalizeTargets(message.getSenderId(), targetUserIds);
        if (decision.updateConversation()) {
            for (String userId : participants) {
                conversationStateStore.setUserMaxSeq(userId, conversationId, message.getSeq());
                userMaxSeqPersistenceWriter.enqueue(userId, conversationId, message.getSeq());
            }
            if (message.getSenderId() != null) {
                conversationStateStore.setUserReadSeq(message.getSenderId(), conversationId, message.getSeq());
            }
        }

        if (decision.updateUnread()) {
            for (String userId : participants) {
                if (!userId.equals(message.getSenderId())) {
                    conversationStateStore.incrementUnread(userId, conversationId);
                }
            }
        }

        if (decision.updateLastMessage()) {
            conversationStateStore.setLastMessageSummary(
                    conversationId,
                    serializeSummary(message, decision.notification())
            );
        }
    }

    /**
     * Pre-process READ_RECEIPT events before seq allocation.
     *
     * Aggregates by (userId=senderId, conversationId) → max seq, then:
     *   1. Writes to Redis synchronously for immediate read-cursor visibility.
     *   2. Enqueues to {@link } for async MongoDB durability.
     */
    public void processReadReceipts(List<Message> events) {
        record ReadSeqKey(String userId, String conversationId) {}
        Map<ReadSeqKey, Long> aggregated = new HashMap<>();

//        for (Message event : events) {
//            if (event == null || event.getSenderId() == null || event.getContent() == null) {
//                continue;
//            }
//            try {
//                ReadReceiptPayload payload = objectMapper.readValue(event.getContent(), ReadReceiptPayload.class);
//                String conversationId = payload.getConversationId();
//                if ((conversationId == null || conversationId.isBlank()) && event.getSessionType() != null) {
//                    conversationId = resolveConversationId(event);
//                }
//                if (payload.getSeq() == null || conversationId == null || conversationId.isBlank()) {
//                    continue;
//                }
//                ReadSeqKey key = new ReadSeqKey(event.getSenderId(), conversationId);
//                aggregated.merge(key, payload.getSeq(), Math::max);
//            } catch (JsonProcessingException e) {
//                log.warn("Failed to parse ReadReceiptPayload content='{}': {}",
//                        decodeContent(event.getContent()), e.getMessage());
//            }
//        }

//        for (Map.Entry<ReadSeqKey, Long> entry : aggregated.entrySet()) {
//            ReadSeqKey key = entry.getKey();
//            long maxSeq = entry.getValue();
//            conversationStateStore.setUserReadSeq(key.userId(), key.conversationId(), maxSeq);
//        }
    }

    /**
     * Apply Redis conversation state for an entire batch in aggregate (P2 optimization).
     *
     * Reduces Redis round-trips from O(N×P) to O(P) by:
     *   - One setConversationMaxSeq per batch (last seq)
     *   - One setUserMaxSeq per participant (last seq)
     *   - One setUserReadSeq per sender (their last sent seq)
     *   - One incrementUnreadBy(delta) per recipient instead of N×INCR
     *   - One setLastMessageSummary per batch (last message)
     */
    public void applyBatch(List<MessageWithTargets> entries) {
        if (entries == null || entries.isEmpty()) return;

        String conversationId = null;
        Message firstMsg = null;
        Message lastMsg  = null;
        boolean anyUpdateConversation = false;
        boolean anyUpdateLastMessage  = false;

        Set<String> allParticipants = new LinkedHashSet<>();
        Map<String, Long>    senderMaxSeq  = new LinkedHashMap<>();
        Map<String, Integer> unreadDelta   = new LinkedHashMap<>();

        for (MessageWithTargets entry : entries) {
            Message msg = entry.message();
            String messageConversationId = resolveConversationId(msg);
            if (msg == null || messageConversationId == null || msg.getSeq() == null) continue;

            if (conversationId == null) {
                conversationId = messageConversationId;
                firstMsg = msg;
            }
            lastMsg = msg;

            MessageRouteDecision decision = new DefaultMessagePolicyEngine().decide(msg);
            anyUpdateConversation |= decision.updateConversation();
            anyUpdateLastMessage  |= decision.updateLastMessage();

            Set<String> participants = normalizeTargets(msg.getSenderId(), entry.targets());
            allParticipants.addAll(participants);

            if (msg.getSenderId() != null) {
                senderMaxSeq.merge(msg.getSenderId(), msg.getSeq(), Math::max);
            }
            if (decision.updateUnread()) {
                for (String uid : participants) {
                    if (!uid.equals(msg.getSenderId())) {
                        unreadDelta.merge(uid, 1, Integer::sum);
                    }
                }
            }
        }

        if (conversationId == null || firstMsg == null || lastMsg == null) return;

        conversationStateStore.setConversationMinSeqIfAbsent(conversationId, firstMsg.getSeq());
        conversationStateStore.setConversationMaxSeq(conversationId, lastMsg.getSeq());

        if (anyUpdateConversation) {
            for (String uid : allParticipants) {
                conversationStateStore.setUserMaxSeq(uid, conversationId, lastMsg.getSeq());
                userMaxSeqPersistenceWriter.enqueue(uid, conversationId, lastMsg.getSeq());
            }
            for (Map.Entry<String, Long> e : senderMaxSeq.entrySet()) {
                conversationStateStore.setUserReadSeq(e.getKey(), conversationId, e.getValue());
            }
        }

        for (Map.Entry<String, Integer> e : unreadDelta.entrySet()) {
            conversationStateStore.incrementUnreadBy(e.getKey(), conversationId, e.getValue());
        }

        if (anyUpdateLastMessage) {
            MessageRouteDecision lastDecision = new DefaultMessagePolicyEngine().decide(lastMsg);
            conversationStateStore.setLastMessageSummary(
                    conversationId,
                    serializeSummary(lastMsg, lastDecision.notification()));
        }
    }

    private Set<String> normalizeTargets(String senderId, List<String> targetUserIds) {
        Set<String> participants = new LinkedHashSet<>();
        if (targetUserIds != null) {
            participants.addAll(targetUserIds);
        }
        if (senderId != null && !senderId.isBlank()) {
            participants.add(senderId);
        }
        return participants;
    }

    private String serializeSummary(Message message, boolean notification) {
        try {
            ConversationLastMessageSummary summary = new ConversationLastMessageSummary();
            summary.setSeq(message.getSeq());
            summary.setSenderId(message.getSenderId());
            summary.setContent(decodeContent(message.getContent()));
            summary.setContentType(message.getContentType() == null ? null : message.getContentType().getCode());
            summary.setPreviewText(MessagePreviewUtil.resolvePreview(
                    message.getContentType() == null ? null : message.getContentType().getCode(),
                    decodeContent(message.getContent()),
                    message.getAttributes()));
            summary.setPreviewType(MessagePreviewUtil.resolvePreviewType(
                    message.getContentType() == null ? null : message.getContentType().getCode(),
                    notification));
            summary.setSendTime(message.getSendTime());
            summary.setNotification(notification);
            return objectMapper.writeValueAsString(summary);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize conversation last message", e);
        }
    }

    private String resolveConversationId(Message message) {
        if (message == null || message.getSessionType() == null) {
            return null;
        }
        return ConversationIdUtil.buildConversationId(message);
    }

    private String decodeContent(byte[] content) {
        if (content == null || content.length == 0) {
            return null;
        }
        return new String(content, StandardCharsets.UTF_8);
    }

}
