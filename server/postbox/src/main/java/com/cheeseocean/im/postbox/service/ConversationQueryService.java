package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.common.core.auth.PermissionCheckRequest;
import com.cheeseocean.im.common.core.auth.PermissionCheckResult;
import com.cheeseocean.im.common.core.auth.SessionPrincipal;
import com.cheeseocean.im.common.core.enums.ConversationAction;
import com.cheeseocean.im.common.core.util.ConversationIdUtil;
import com.cheeseocean.im.postbox.api.ConversationSummaryResponse;
import com.cheeseocean.im.postbox.history.MessageIdMappingDoc;
import com.cheeseocean.im.postbox.history.MessageSlot;
import com.cheeseocean.im.postbox.permission.ConversationPermissionService;
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

    private final BlockMessageQueryService blockMessageQueryService;
    private final StringRedisTemplate redisTemplate;
    private final ConversationPermissionService conversationPermissionService;

    public ConversationQueryService(BlockMessageQueryService blockMessageQueryService,
                                    StringRedisTemplate redisTemplate,
                                    ConversationPermissionService conversationPermissionService) {
        this.blockMessageQueryService = blockMessageQueryService;
        this.redisTemplate = redisTemplate;
        this.conversationPermissionService = conversationPermissionService;
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

        ConversationSummaryResponse response = new ConversationSummaryResponse();
        response.setConversationId(accumulator.latestMapping.getConversationId());
        response.setKind(detectKind(accumulator.latestMapping.getConversationId()));
        response.setTitle(resolveTitle(accumulator.latestMapping.getConversationId(), currentUserId));
        response.setSubtitle(resolveSubtitle(accumulator.latestMapping.getConversationId()));
        response.setPeerUserId(ConversationIdUtil.peerUser(accumulator.latestMapping.getConversationId(), currentUserId));
        response.setLastMessagePreview(resolvePreview(message));
        response.setLastMessageTime(resolveTime(message.getSendTime(), accumulator.latestMapping.getSendTime()));
        response.setUnreadCount(accumulator.unreadCount);
        response.setAccentColor(pickAccentColor(accumulator.latestMapping.getConversationId()));
        return response;
    }

    private String detectKind(String conversationId) {
        if (conversationId.startsWith("c1:")) {
            return "DIRECT";
        }
        if (conversationId.startsWith("c2:")) {
            return "GROUP";
        }
        if (conversationId.startsWith("c4:")) {
            return "CHANNEL";
        }
        return "DIRECT";
    }

    private String resolveTitle(String conversationId, String currentUserId) {
        String[] parts = conversationId.split(":");
        if (conversationId.startsWith("c1:") && parts.length == 3) {
            return currentUserId.equals(parts[1]) ? parts[2] : parts[1];
        }
        if (parts.length >= 2) {
            return parts[1];
        }
        return conversationId;
    }

    private String resolveSubtitle(String conversationId) {
        if (conversationId.startsWith("c1:")) {
            return "Direct conversation";
        }
        if (conversationId.startsWith("c2:")) {
            return "Group conversation";
        }
        if (conversationId.startsWith("c4:")) {
            return "Channel conversation";
        }
        return "Conversation";
    }

    private String resolvePreview(MessageSlot message) {
        if (StringUtils.hasText(message.getContent())) {
            return message.getContent();
        }
        if (message.getExt() != null && !message.getExt().isEmpty()) {
            return "Attachment";
        }
        return "Unsupported message";
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
