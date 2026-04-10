package com.cheeseocean.im.apiserver.facade;

import com.cheeseocean.im.apiserver.model.request.BatchGetConversationsRequest;
import com.cheeseocean.im.apiserver.model.request.GetConversationRequest;
import com.cheeseocean.im.apiserver.model.request.ListConversationMessagesRequest;
import com.cheeseocean.im.apiserver.model.request.ListConversationsRequest;
import com.cheeseocean.im.apiserver.model.request.SetConversationsRequest;
import com.cheeseocean.im.apiserver.model.response.ConversationIdsHashResponse;
import com.cheeseocean.im.apiserver.model.response.ConversationIdsResponse;
import com.cheeseocean.im.apiserver.model.response.ConversationResponse;
import com.cheeseocean.im.apiserver.model.response.HistoryMessageResponse;
import com.cheeseocean.im.common.api.business.domain.User;
import com.cheeseocean.im.common.api.business.domain.UserConversation;
import com.cheeseocean.im.common.api.conversation.ConversationService;
import com.cheeseocean.im.common.api.enums.ConversationKind;
import com.cheeseocean.im.common.api.permission.ConversationPermissionRequest;
import com.cheeseocean.im.common.api.session.SessionPrincipal;
import com.cheeseocean.im.common.api.user.UserInfoService;
import com.cheeseocean.im.common.core.auth.PermissionCheckResult;
import com.cheeseocean.im.postbox.service.HistoryQueryService;
import com.cheeseocean.im.postbox.service.ConversationPermissionService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * 会话 HTTP facade，负责处理登录态解析和 HTTP 查询编排。
 */
@Service
public class ConversationFacade {

    private final ConversationService conversationService;
    private final ConversationPermissionService permissionService;
    private final HistoryQueryService historyQueryService;

    @DubboReference(check = false)
    private UserInfoService userInfoService;

    public ConversationFacade(ConversationService conversationService,
                              ConversationPermissionService permissionService,
                              HistoryQueryService historyQueryService) {
        this.conversationService = conversationService;
        this.permissionService = permissionService;
        this.historyQueryService = historyQueryService;
    }

    public List<ConversationResponse> listConversations(SessionPrincipal session, ListConversationsRequest request) {
        int effectiveLimit = Math.max(1, request.getLimit());
        return conversationService.getAllConversations(session.getUserId()).stream()
                .filter(conversation -> conversation != null && conversation.getConversationId() != null)
                .filter(conversation -> allow(session, conversation.getConversationId()))
                .sorted(Comparator.comparingLong(ConversationFacade::sortTime).reversed())
                .limit(effectiveLimit)
                .map(this::toConversationResponse)
                .filter(response -> response != null)
                .toList();
    }

    public List<ConversationResponse> getAllConversations(SessionPrincipal session) {
        return conversationService.getAllConversations(session.getUserId()).stream()
                .map(this::toConversationResponse)
                .toList();
    }

    public ConversationResponse getConversation(SessionPrincipal session, GetConversationRequest request) {
        return toConversationResponse(conversationService.getConversation(session.getUserId(), request.getConversationId()));
    }

    public List<ConversationResponse> getConversations(SessionPrincipal session, BatchGetConversationsRequest request) {
        return conversationService.getConversations(session.getUserId(), request.getConversationIds()).stream()
                .map(this::toConversationResponse)
                .toList();
    }

    public ConversationIdsResponse getConversationIds(SessionPrincipal session) {
        ConversationIdsResponse response = new ConversationIdsResponse();
        response.setConversationIds(conversationService.getConversationIds(session.getUserId()));
        return response;
    }

    public ConversationIdsHashResponse getConversationIdsHash(SessionPrincipal session) {
        ConversationIdsHashResponse response = new ConversationIdsHashResponse();
        response.setHash(conversationService.getConversationIdsHash(session.getUserId()));
        return response;
    }

    public ConversationIdsResponse getNotNotifyConversationIds(SessionPrincipal session) {
        ConversationIdsResponse response = new ConversationIdsResponse();
        response.setConversationIds(conversationService.getNotNotifyConversationIds(session.getUserId()));
        return response;
    }

    public ConversationIdsResponse getPinnedConversationIds(SessionPrincipal session) {
        ConversationIdsResponse response = new ConversationIdsResponse();
        response.setConversationIds(conversationService.getPinnedConversationIds(session.getUserId()));
        return response;
    }

    public void setConversations(SessionPrincipal session, SetConversationsRequest request) {
        conversationService.setConversations(List.of(session.getUserId()), request.getPayload());
    }

    public List<HistoryMessageResponse> getConversationMessages(SessionPrincipal session, ListConversationMessagesRequest request) {
        return historyQueryService.getConversationMessages(session, request.getConversationId(), request.getLimit()).stream()
                .map(message -> {
                    HistoryMessageResponse response = new HistoryMessageResponse();
                    response.setSequence(message.getSequence());
                    response.setServerMsgId(message.getServerMsgId());
                    response.setSenderId(message.getSenderId());
                    response.setSenderName(message.getSenderName());
                    response.setContent(message.getContent());
                    response.setPreviewType(message.getPreviewType());
                    response.setSendTime(message.getSendTime());
                    return response;
                })
                .toList();
    }

    private ConversationResponse toConversationResponse(UserConversation conversation) {
        if (conversation == null) {
            return null;
        }
        ConversationResponse response = new ConversationResponse();
        response.setOwnerUserId(conversation.getOwnerUserId());
        response.setConversationId(conversation.getConversationId());
        response.setConversationType(conversation.getConversationType());
        response.setTargetId(conversation.getTargetId());
        response.setReceiveOpt(conversation.getReceiveOpt());
        response.setUnreadCount(conversation.getUnreadCount());
        response.setPinned(conversation.isPinned());
        response.setAttachedInfo(conversation.getAttachedInfo());
        response.setGroupAtType(conversation.getGroupAtType());
        response.setAutoCleanup(conversation.isAutoCleanup());
        response.setCleanupCycle(conversation.getCleanupCycle());
        response.setLatestCleanupTime(conversation.getLatestCleanupTime());
        response.setCreatedAt(conversation.getCreatedAt());
        response.setUpdatedAt(conversation.getUpdatedAt());
        response.setKind(resolveKind(conversation));
        response.setTitle(resolveTitle(conversation));
        response.setSubtitle(resolveSubtitle(conversation));
        response.setNotification(resolveKind(conversation) == ConversationKind.NOTIFICATION);
        return response;
    }

    private ConversationKind resolveKind(UserConversation conversation) {
        return switch (conversation.getConversationType()) {
            case 2 -> ConversationKind.GROUP;
            case 3 -> ConversationKind.NOTIFICATION;
            default -> ConversationKind.DIRECT;
        };
    }

    private String resolveTitle(UserConversation conversation) {
        return switch (resolveKind(conversation)) {
            case GROUP -> defaultValue(conversation.getTargetId(), conversation.getConversationId());
            case NOTIFICATION -> "System notifications";
            case CHANNEL -> defaultValue(conversation.getTargetId(), conversation.getConversationId());
            case DIRECT -> resolveDirectTitle(conversation.getTargetId(), conversation.getConversationId());
        };
    }

    private String resolveSubtitle(UserConversation conversation) {
        return switch (resolveKind(conversation)) {
            case GROUP -> "Group conversation";
            case NOTIFICATION -> "Notification conversation";
            case CHANNEL -> "Channel conversation";
            case DIRECT -> "Direct conversation";
        };
    }

    private String resolveDirectTitle(String targetId, String fallback) {
        String userId = defaultValue(targetId, fallback);
        if (userId == null || userId.isBlank() || userInfoService == null) {
            return userId;
        }
        try {
            User user = userInfoService.getUserInfo(userId);
            if (user != null && user.getNickname() != null && !user.getNickname().isBlank()) {
                return user.getNickname();
            }
        } catch (Exception ignored) {
            // 用户资料服务异常时降级展示 targetId，避免会话列表整体失败。
        }
        return userId;
    }

    private String defaultValue(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback == null ? "" : fallback;
    }

    private boolean allow(SessionPrincipal session, String conversationId) {
        if (permissionService == null) {
            return true;
        }
        ConversationPermissionRequest request = new ConversationPermissionRequest();
        request.setTenantId(session.getTenantId());
        request.setUserId(session.getUserId());
        request.setConversationId(conversationId);
        PermissionCheckResult result = permissionService.check(request);
        return result == null || result.isAllowed();
    }

    private static long sortTime(UserConversation conversation) {
        if (conversation == null) {
            return Long.MIN_VALUE;
        }
        return conversation.getUpdatedAt();
    }
}
