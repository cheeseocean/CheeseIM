package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.api.business.domain.User;
import com.cheeseocean.im.common.api.business.domain.UserConversation;
import com.cheeseocean.im.common.api.enums.ConversationKind;
import com.cheeseocean.im.common.api.permission.ConversationPermissionRequest;
import com.cheeseocean.im.common.api.session.SessionPrincipal;
import com.cheeseocean.im.common.core.auth.PermissionCheckResult;
import com.cheeseocean.im.postbox.api.ConversationSummaryResponse;
import com.cheeseocean.im.postbox.facade.UserServiceFacade;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * CheeseBox 会话列表查询服务。
 *
 * @author xxxcrel
 */
@Service
public class ConversationService {

    private final ConversationPermissionService permissionService;
    private final UserServiceFacade userServiceFacade;

    @DubboReference
    private com.cheeseocean.im.common.api.conversation.ConversationService conversationService;

    public ConversationService(ConversationPermissionService permissionService,
                               UserServiceFacade userServiceFacade) {
        this.permissionService = permissionService;
        this.userServiceFacade = userServiceFacade;
    }

    /**
     * 查询当前用户最近会话卡片。
     */
    public List<ConversationSummaryResponse> listConversations(SessionPrincipal session, int limit) {
        if (session == null || session.getUserId() == null || session.getUserId().isBlank()) {
            return List.of();
        }
        int effectiveLimit = Math.max(1, limit);
        List<UserConversation> conversations = conversationService.getAllConversations(session.getUserId());
        if (conversations == null || conversations.isEmpty()) {
            return List.of();
        }
        return conversations.stream()
                .filter(conversation -> conversation != null && conversation.getConversationId() != null)
                .filter(conversation -> allow(session, conversation.getConversationId()))
                .sorted(Comparator.comparingLong(ConversationService::sortTime).reversed())
                .limit(effectiveLimit)
                .map(conversation -> toConversationSummary(session, conversation))
                .filter(response -> response != null)
                .toList();
    }

    private ConversationSummaryResponse toConversationSummary(SessionPrincipal session, UserConversation conversation) {
        ConversationSummaryResponse response = new ConversationSummaryResponse();
        response.setConversationId(conversation.getConversationId());
        response.setKind(resolveKind(conversation));
        response.setTitle(resolveTitle(conversation));
        response.setSubtitle(resolveSubtitle(conversation));
        response.setUnreadCount(conversation.getUnreadCount());
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
        if (userId == null || userId.isBlank() || userServiceFacade == null) {
            return userId;
        }
        try {
            User userInfo = userServiceFacade.getUserInfo(userId);
            if (userInfo != null && userInfo.getNickname() != null && !userInfo.getNickname().isBlank()) {
                return userInfo.getNickname();
            }
        } catch (Exception ignored) {
            // CheeseBox 会话列表允许在用户资料服务暂不可用时降级为 targetId 展示。
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
