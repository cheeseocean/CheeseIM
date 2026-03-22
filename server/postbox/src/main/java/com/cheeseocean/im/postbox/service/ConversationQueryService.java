package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.api.dto.message.ConversationLastMessageSummary;
import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.common.core.auth.PermissionCheckRequest;
import com.cheeseocean.im.common.core.auth.PermissionCheckResult;
import com.cheeseocean.im.common.core.auth.SessionPrincipal;
import com.cheeseocean.im.common.core.enums.ConversationAction;
import com.cheeseocean.im.common.core.enums.ConversationKind;
import com.cheeseocean.im.common.core.util.ConversationIdUtil;
import com.cheeseocean.im.postbox.api.ConversationSummaryResponse;
import com.cheeseocean.im.postbox.history.MessageIdMappingDoc;
import com.cheeseocean.im.postbox.history.MessageSlot;
import com.cheeseocean.im.postbox.permission.ConversationPermissionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ConversationQueryService {

    private static final List<String> ACCENT_COLORS = List.of(
            "#6ef1c6",
            "#79d7ff",
            "#f8b56a",
            "#ff8f7a",
            "#99a8ff",
            "#8ce0b8"
    );
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final BlockMessageQueryService blockMessageQueryService;
    private final StringRedisTemplate redisTemplate;
    private final ConversationPermissionService conversationPermissionService;
    private final MessagePreviewResolver messagePreviewResolver;
    private final ConversationPresentationResolver conversationPresentationResolver;

    public ConversationQueryService(BlockMessageQueryService blockMessageQueryService,
                                    StringRedisTemplate redisTemplate,
                                    ConversationPermissionService conversationPermissionService,
                                    MessagePreviewResolver messagePreviewResolver,
                                    ConversationPresentationResolver conversationPresentationResolver) {
        this.blockMessageQueryService = blockMessageQueryService;
        this.redisTemplate = redisTemplate;
        this.conversationPermissionService = conversationPermissionService;
        this.messagePreviewResolver = messagePreviewResolver;
        this.conversationPresentationResolver = conversationPresentationResolver;
    }

    public List<ConversationSummaryResponse> listConversations(SessionPrincipal session, int limit) {
        List<MessageIdMappingDoc> mappings = blockMessageQueryService.findRecentConversationMappings(Math.max(limit * 5, 50));
        Map<String, ConversationAccumulator> byConversation = new LinkedHashMap<>();
        for (MessageIdMappingDoc mapping : mappings) {
            if (mapping.getConversationId() == null || !mayBelongToUser(session.getUserId(), mapping.getConversationId())) {
                continue;
            }
            ConversationAccumulator accumulator = byConversation.computeIfAbsent(
                    mapping.getConversationId(),
                    ignored -> new ConversationAccumulator()
            );
            if (accumulator.latestMapping == null) {
                accumulator.latestMapping = mapping;
            }
            accumulator.unreadCount = loadUnreadCount(session.getUserId(), mapping.getConversationId());
        }

        List<ConversationAccumulator> visibleAccumulators = new ArrayList<>();
        for (Map.Entry<String, ConversationAccumulator> entry : byConversation.entrySet()) {
            if (visibleAccumulators.size() >= limit) {
                break;
            }
            PermissionCheckRequest request = new PermissionCheckRequest();
            request.setTenantId(session.getTenantId());
            request.setUserId(session.getUserId());
            request.setSessionId(session.getSessionId());
            request.setDeviceId(session.getDeviceId());
            request.setConversationId(entry.getKey());
            request.setAction(ConversationAction.READ.name());
            PermissionCheckResult permission = conversationPermissionService.check(request);
            if (permission != null && permission.isAllowed()) {
                visibleAccumulators.add(entry.getValue());
            }
        }

        return visibleAccumulators.stream()
                .map(accumulator -> toResponse(session.getUserId(), accumulator))
                .filter(response -> response != null)
                .toList();
    }

    private ConversationSummaryResponse toResponse(String currentUserId,
                                                   ConversationAccumulator accumulator) {
        MessageSlot message = blockMessageQueryService.findSlot(
                accumulator.latestMapping.getConversationId(),
                accumulator.latestMapping.getSeq());
        if (message == null) {
            return null;
        }

        ConversationKind kind = conversationPresentationResolver.resolveKind(accumulator.latestMapping.getConversationId());
        ConversationSummaryResponse response = new ConversationSummaryResponse();
        response.setConversationId(accumulator.latestMapping.getConversationId());
        response.setKind(kind);
        response.setTitle(conversationPresentationResolver.resolveTitle(accumulator.latestMapping.getConversationId(), currentUserId));
        response.setSubtitle(conversationPresentationResolver.resolveSubtitle(kind));
        response.setPeerUserId(ConversationIdUtil.peerUser(accumulator.latestMapping.getConversationId(), currentUserId));
        ConversationLastMessageSummary summary = loadLastMessageSummary(accumulator.latestMapping.getConversationId());
        if (summary != null) {
            response.setLastMessagePreview(summary.getPreviewText() != null ? summary.getPreviewText() : summary.getContent());
            response.setLastMessagePreviewType(summary.getPreviewType());
            response.setLastMessageTime(summary.getSendTime());
            response.setNotification(summary.isNotification());
        } else if (shouldShowLastMessage(message)) {
            response.setLastMessagePreview(messagePreviewResolver.resolvePreview(message));
            response.setLastMessagePreviewType(messagePreviewResolver.resolvePreviewType(message));
            response.setLastMessageTime(resolveTime(message.getSendTime(), accumulator.latestMapping.getSendTime()));
            response.setNotification(message.getOptions() != null && Boolean.TRUE.equals(message.getOptions().isNotification()));
        }
        response.setUnreadCount(accumulator.unreadCount);
        response.setAccentColor(pickAccentColor(accumulator.latestMapping.getConversationId()));
        return response;
    }

    private ConversationLastMessageSummary loadLastMessageSummary(String conversationId) {
        String raw = redisTemplate.opsForValue().get(RedisKeys.convLastMsg(conversationId));
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(raw, ConversationLastMessageSummary.class);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean shouldShowLastMessage(MessageSlot message) {
        return message.getOptions() == null || !Boolean.FALSE.equals(message.getOptions().isNeedLastMessage());
    }

    private Long resolveTime(Long sendTime, Long mappingSendTime) {
        Long candidate = sendTime != null ? sendTime : mappingSendTime;
        return candidate == null ? System.currentTimeMillis() : candidate;
    }

    private int loadUnreadCount(String userId, String conversationId) {
        String raw = redisTemplate.opsForValue().get(RedisKeys.userUnread(userId, conversationId));
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private boolean mayBelongToUser(String userId, String conversationId) {
        if (conversationId == null || userId == null) {
            return false;
        }
        if (conversationId.startsWith("c1:")) {
            return conversationId.endsWith(":" + userId) || conversationId.contains(":" + userId + ":");
        }
        return true;
    }

    private String pickAccentColor(String conversationId) {
        int index = Math.floorMod(conversationId.hashCode(), ACCENT_COLORS.size());
        return ACCENT_COLORS.get(index);
    }

    private static final class ConversationAccumulator {
        private MessageIdMappingDoc latestMapping;
        private int unreadCount;
    }
}
