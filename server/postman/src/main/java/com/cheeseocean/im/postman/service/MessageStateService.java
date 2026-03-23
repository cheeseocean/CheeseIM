package com.cheeseocean.im.postman.service;

import com.cheeseocean.im.common.api.dto.message.ConversationLastMessageSummary;
import com.cheeseocean.im.common.api.dto.message.SequencedMessage;
import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.common.core.enums.ContentType;
import com.cheeseocean.im.common.core.util.MessagePreviewUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class MessageStateService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public MessageStateService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void apply(SequencedMessage message, List<String> targetUserIds) {
        if (message == null || message.getConversationId() == null || message.getSeq() == null) {
            return;
        }
        guardUnsupportedContent(message);

        MessageRouteDecision decision = new DefaultMessagePolicyEngine().decide(toIngressEvent(message));
        if (!decision.updateConversation() && !decision.updateUnread() && !decision.updateLastMessage()) {
            return;
        }

        redisTemplate.opsForValue().setIfAbsent(
                RedisKeys.convMinSeq(message.getConversationId()),
                String.valueOf(message.getSeq()));

        Set<String> participants = normalizeTargets(message.getSenderId(), targetUserIds);
        if (decision.updateConversation()) {
            for (String userId : participants) {
                redisTemplate.opsForValue().set(
                        RedisKeys.userMaxSeq(userId, message.getConversationId()),
                        String.valueOf(message.getSeq()));
            }
            if (message.getSenderId() != null) {
                redisTemplate.opsForValue().set(
                        RedisKeys.userReadSeq(message.getSenderId(), message.getConversationId()),
                        String.valueOf(message.getSeq()));
            }
        }

        if (decision.updateUnread()) {
            for (String userId : participants) {
                if (!userId.equals(message.getSenderId())) {
                    redisTemplate.opsForValue().increment(RedisKeys.userUnread(userId, message.getConversationId()));
                }
            }
        }

        if (decision.updateLastMessage()) {
            redisTemplate.opsForValue().set(
                    RedisKeys.convLastMsg(message.getConversationId()),
                    serializeSummary(message, decision.notification()));
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

    private void guardUnsupportedContent(SequencedMessage message) {
        if (message.getContentType() == null) {
            return;
        }
        try {
            if (ContentType.fromCode(message.getContentType()) == ContentType.READ_RECEIPT) {
                throw new IllegalStateException("READ_RECEIPT must not mutate message state");
            }
        } catch (IllegalArgumentException ignored) {
            // Preserve existing behavior for unknown content types.
        }
    }
}
