package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.enums.ConversationAction;
import com.cheeseocean.im.common.model.auth.PermissionCheckRequest;
import com.cheeseocean.im.common.model.auth.PermissionCheckResult;
import com.cheeseocean.im.common.model.auth.SessionPrincipal;
import com.cheeseocean.im.postbox.api.ConversationSummaryResponse;
import com.cheeseocean.im.postbox.entity.InboxDocument;
import com.cheeseocean.im.postbox.entity.MessageDocument;
import com.cheeseocean.im.postbox.permission.ConversationPermissionService;
import com.cheeseocean.im.postbox.repository.InboxDocumentRepository;
import com.cheeseocean.im.postbox.repository.MessageDocumentRepository;
import com.cheeseocean.im.common.utils.ConversationIds;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    private final InboxDocumentRepository inboxDocumentRepository;
    private final MessageDocumentRepository messageDocumentRepository;
    private final ConversationPermissionService conversationPermissionService;

    public ConversationQueryService(InboxDocumentRepository inboxDocumentRepository,
                                    MessageDocumentRepository messageDocumentRepository,
                                    ConversationPermissionService conversationPermissionService) {
        this.inboxDocumentRepository = inboxDocumentRepository;
        this.messageDocumentRepository = messageDocumentRepository;
        this.conversationPermissionService = conversationPermissionService;
    }

    public List<ConversationSummaryResponse> listConversations(SessionPrincipal session, int limit) {
        List<InboxDocument> inboxItems = inboxDocumentRepository.findByUserIdOrderBySequenceDesc(session.getUserId());
        Map<String, ConversationAccumulator> byConversation = new LinkedHashMap<>();
        for (InboxDocument inbox : inboxItems) {
            ConversationAccumulator accumulator = byConversation.computeIfAbsent(
                    inbox.getConversationId(),
                    ignored -> new ConversationAccumulator()
            );
            if (accumulator.latestInbox == null) {
                accumulator.latestInbox = inbox;
            }
            if (!inbox.isRead()) {
                accumulator.unreadCount++;
            }
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

        Map<String, MessageDocument> messageById = messageDocumentRepository.findAllById(
                        visibleAccumulators.stream()
                                .map(accumulator -> accumulator.latestInbox.getServerMsgId())
                                .toList())
                .stream()
                .collect(Collectors.toMap(MessageDocument::getServerMsgId, Function.identity()));

        return visibleAccumulators.stream()
                .map(accumulator -> toResponse(session.getUserId(), accumulator, messageById.get(accumulator.latestInbox.getServerMsgId())))
                .filter(response -> response != null)
                .toList();
    }

    private ConversationSummaryResponse toResponse(String currentUserId,
                                                   ConversationAccumulator accumulator,
                                                   MessageDocument message) {
        if (message == null) {
            return null;
        }

        ConversationSummaryResponse response = new ConversationSummaryResponse();
        response.setConversationId(accumulator.latestInbox.getConversationId());
        response.setKind(detectKind(accumulator.latestInbox.getConversationId()));
        response.setTitle(resolveTitle(accumulator.latestInbox.getConversationId(), currentUserId));
        response.setSubtitle(resolveSubtitle(accumulator.latestInbox.getConversationId()));
        response.setPeerUserId(ConversationIds.peerUser(accumulator.latestInbox.getConversationId(), currentUserId));
        response.setLastMessagePreview(resolvePreview(message));
        response.setLastMessageTime(resolveTime(message.getCreatedAt(), accumulator.latestInbox.getCreatedAt()));
        response.setUnreadCount(accumulator.unreadCount);
        response.setAccentColor(pickAccentColor(accumulator.latestInbox.getConversationId()));
        return response;
    }

    private String detectKind(String conversationId) {
        if (conversationId.startsWith("single:")) {
            return "DIRECT";
        }
        if (conversationId.startsWith("group:")) {
            return "GROUP";
        }
        if (conversationId.startsWith("channel:")) {
            return "CHANNEL";
        }
        return "DIRECT";
    }

    private String resolveTitle(String conversationId, String currentUserId) {
        String[] parts = conversationId.split(":");
        if (conversationId.startsWith("single:") && parts.length == 3) {
            return currentUserId.equals(parts[1]) ? parts[2] : parts[1];
        }
        if (parts.length >= 2) {
            return parts[1];
        }
        return conversationId;
    }

    private String resolveSubtitle(String conversationId) {
        if (conversationId.startsWith("single:")) {
            return "Direct conversation";
        }
        if (conversationId.startsWith("group:")) {
            return "Group conversation";
        }
        if (conversationId.startsWith("channel:")) {
            return "Channel conversation";
        }
        return "Conversation";
    }

    private String resolvePreview(MessageDocument message) {
        if (StringUtils.hasText(message.getContent())) {
            return message.getContent();
        }
        if (StringUtils.hasText(message.getAttachedInfo())) {
            return "Attachment";
        }
        return "Unsupported message";
    }

    private Long resolveTime(Instant createdAt, Instant inboxCreatedAt) {
        Instant candidate = createdAt != null ? createdAt : inboxCreatedAt;
        return candidate == null ? System.currentTimeMillis() : candidate.toEpochMilli();
    }

    private String pickAccentColor(String conversationId) {
        int index = Math.floorMod(conversationId.hashCode(), ACCENT_COLORS.size());
        return ACCENT_COLORS.get(index);
    }

    private static final class ConversationAccumulator {
        private InboxDocument latestInbox;
        private int unreadCount;
    }
}
