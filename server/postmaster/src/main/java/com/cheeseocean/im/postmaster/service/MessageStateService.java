package com.cheeseocean.im.postmaster.service;

import com.cheeseocean.im.common.api.dto.message.ConversationLastMessageSummary;
import com.cheeseocean.im.common.api.dto.message.ReadReceiptPayload;
import com.cheeseocean.im.common.api.dto.message.SequencedMessage;
import com.cheeseocean.im.common.api.event.IngressEvent;
import com.cheeseocean.im.postmaster.service.ConversationSyncFacade;
import com.cheeseocean.im.common.core.store.conversation.ConversationStateStore;
import com.cheeseocean.im.common.core.util.MessagePreviewUtil;
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

@Service
public class MessageStateService {

    private static final Logger log = CommonLoggers.POSTMASTER;

    private final ConversationStateStore conversationStateStore;
    private final ObjectMapper objectMapper;
    private final ConversationSyncFacade conversationSyncService;

    public MessageStateService(ConversationStateStore conversationStateStore,
                               ObjectMapper objectMapper,
                               ConversationSyncFacade conversationSyncService) {
        this.conversationStateStore = conversationStateStore;
        this.objectMapper = objectMapper;
        this.conversationSyncService = conversationSyncService;
    }

    public void apply(SequencedMessage message, List<String> targetUserIds) {
        if (message == null || message.getConversationId() == null || message.getSeq() == null) {
            return;
        }

        MessageRouteDecision decision = new DefaultMessagePolicyEngine().decide(toIngressEvent(message));
        if (!decision.updateConversation() && !decision.updateUnread() && !decision.updateLastMessage()) {
            return;
        }

        conversationStateStore.setConversationMinSeqIfAbsent(message.getConversationId(), message.getSeq());
        conversationStateStore.setConversationMaxSeq(message.getConversationId(), message.getSeq());

        Set<String> participants = normalizeTargets(message.getSenderId(), targetUserIds);
        if (decision.updateConversation()) {
            for (String userId : participants) {
                conversationStateStore.setUserMaxSeq(userId, message.getConversationId(), message.getSeq());
            }
            if (message.getSenderId() != null) {
                conversationStateStore.setUserReadSeq(message.getSenderId(), message.getConversationId(), message.getSeq());
            }
        }

        if (decision.updateUnread()) {
            for (String userId : participants) {
                if (!userId.equals(message.getSenderId())) {
                    conversationStateStore.incrementUnread(userId, message.getConversationId());
                }
            }
        }

        if (decision.updateLastMessage()) {
            conversationStateStore.setLastMessageSummary(
                    message.getConversationId(),
                    serializeSummary(message, decision.notification())
            );
        }
    }

    /**
     * Pre-process READ_RECEIPT events before seq allocation (Go's doSetReadSeq equivalent).
     *
     * Aggregates by (userId=senderId, conversationId) → max seq, then:
     *   1. Writes to Redis synchronously for immediate read-cursor visibility.
     *   2. Enqueues to {@link ReadSeqPersistenceWriter} for async MongoDB durability.
     */
    public void processReadReceipts(List<IngressEvent> events) {
        record ReadSeqKey(String userId, String conversationId) {}
        Map<ReadSeqKey, Long> aggregated = new HashMap<>();

        for (IngressEvent event : events) {
            if (event.getSenderId() == null || event.getConversationId() == null
                    || event.getContent() == null) {
                continue;
            }
            try {
                ReadReceiptPayload payload = objectMapper.readValue(event.getContent(), ReadReceiptPayload.class);
                if (payload.getSeq() == null) continue;
                ReadSeqKey key = new ReadSeqKey(event.getSenderId(), event.getConversationId());
                aggregated.merge(key, payload.getSeq(), Math::max);
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse ReadReceiptPayload content='{}': {}", event.getContent(), e.getMessage());
            }
        }

        for (Map.Entry<ReadSeqKey, Long> entry : aggregated.entrySet()) {
            ReadSeqKey key = entry.getKey();
            long maxSeq = entry.getValue();
            conversationStateStore.setUserReadSeq(key.userId(), key.conversationId(), maxSeq);
            if (conversationSyncService != null) {
                conversationSyncService.markRead(key.userId(), key.conversationId(), maxSeq);
            }
        }
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
        SequencedMessage firstMsg = null;
        SequencedMessage lastMsg  = null;
        boolean anyUpdateConversation = false;
        boolean anyUpdateLastMessage  = false;

        Set<String> allParticipants = new LinkedHashSet<>();
        Map<String, Long>    senderMaxSeq  = new LinkedHashMap<>();
        Map<String, Integer> unreadDelta   = new LinkedHashMap<>();

        for (MessageWithTargets entry : entries) {
            SequencedMessage msg = entry.message();
            if (msg == null || msg.getConversationId() == null || msg.getSeq() == null) continue;

            if (conversationId == null) {
                conversationId = msg.getConversationId();
                firstMsg = msg;
            }
            lastMsg = msg;

            MessageRouteDecision decision = new DefaultMessagePolicyEngine().decide(toIngressEvent(msg));
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
            }
            for (Map.Entry<String, Long> e : senderMaxSeq.entrySet()) {
                conversationStateStore.setUserReadSeq(e.getKey(), conversationId, e.getValue());
            }
        }

        for (Map.Entry<String, Integer> e : unreadDelta.entrySet()) {
            conversationStateStore.incrementUnreadBy(e.getKey(), conversationId, e.getValue());
        }

        if (anyUpdateLastMessage) {
            MessageRouteDecision lastDecision = new DefaultMessagePolicyEngine().decide(toIngressEvent(lastMsg));
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

    private String serializeSummary(SequencedMessage message, boolean notification) {
        try {
            ConversationLastMessageSummary summary = new ConversationLastMessageSummary();
            summary.setSeq(message.getSeq());
            summary.setSenderId(message.getSenderId());
            summary.setContent(message.getContent());
            summary.setContentType(message.getContentType());
            summary.setPreviewText(MessagePreviewUtil.resolvePreview(
                    message.getContentType(),
                    message.getContent(),
                    message.getExt()));
            summary.setPreviewType(MessagePreviewUtil.resolvePreviewType(
                    message.getContentType(),
                    notification));
            summary.setSendTime(message.getSendTime());
            summary.setNotification(notification);
            return objectMapper.writeValueAsString(summary);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize conversation last message", e);
        }
    }

    private com.cheeseocean.im.common.api.event.IngressEvent toIngressEvent(SequencedMessage message) {
        com.cheeseocean.im.common.api.event.IngressEvent event = new com.cheeseocean.im.common.api.event.IngressEvent();
        event.setOptions(message.getOptions());
        return event;
    }

}
