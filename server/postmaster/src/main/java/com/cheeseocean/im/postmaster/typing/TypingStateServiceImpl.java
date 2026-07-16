package com.cheeseocean.im.postmaster.typing;

import com.cheeseocean.im.common.api.conversation.TypingStateService;
import com.cheeseocean.im.common.api.dto.conversation.TypingSignal;
import com.cheeseocean.im.common.api.dto.dispatch.ControlNotificationReq;
import com.cheeseocean.im.common.api.enums.CommandType;
import com.cheeseocean.im.common.api.enums.GroupTypeEnum;
import com.cheeseocean.im.common.api.enums.TypingActionEnum;
import com.cheeseocean.im.common.api.permission.ConversationPermissionService;
import com.cheeseocean.im.common.api.permission.ConversationPermissionRequest;
import com.cheeseocean.im.common.api.protocol.ServerEnvelope;
import com.cheeseocean.im.common.api.rpc.ControlNotificationDispatcher;
import com.cheeseocean.im.common.api.permission.PermissionCheckResult;
import com.cheeseocean.im.common.core.store.typing.TypingStateStore;
import com.cheeseocean.im.common.core.metrics.ImMetrics;
import com.cheeseocean.im.postmaster.service.GroupMembershipFacade;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Value;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 输入中瞬时控制信号实现。
 *
 * <p>只校验会话成员并向当前在线目标分发，不创建消息、会话、seq 或离线推送。START 使用服务端
 * TTL，客户端即使漏掉 STOP 也能在过期后自行清理状态。
 */
@DubboService(retries = 0)
public class TypingStateServiceImpl implements TypingStateService {

    private static final int MIN_TTL_SECONDS = 3;
    private static final int MAX_TTL_SECONDS = 5;
    private final GroupMembershipFacade groupMembershipFacade;
    private final TypingStateStore typingStateStore;
    private final int defaultTtlSeconds;
    private final int maxNormalGroupMembers;

    @DubboReference(check = false)
    private ConversationPermissionService conversationPermissionService;

    @DubboReference(check = false, retries = 0)
    private ControlNotificationDispatcher controlNotificationDispatcher;

    public TypingStateServiceImpl(GroupMembershipFacade groupMembershipFacade,
                                  TypingStateStore typingStateStore,
                                  @Value("${cheeseim.typing.default-ttl-seconds:4}") int defaultTtlSeconds,
                                  @Value("${cheeseim.typing.max-normal-group-members:100}") int maxNormalGroupMembers) {
        this.groupMembershipFacade = groupMembershipFacade;
        this.typingStateStore = typingStateStore;
        this.defaultTtlSeconds = clampTtl(defaultTtlSeconds);
        this.maxNormalGroupMembers = Math.max(1, maxNormalGroupMembers);
    }

    @Override
    public TypingSignal publish(String senderId, String conversationId, TypingActionEnum action, int ttlSeconds) {
        if (isBlank(senderId) || isBlank(conversationId) || action == null || !isConversationMember(senderId, conversationId)) {
            ImMetrics.typing("rejected");
            return null;
        }
        TargetResolution targetResolution = notificationTargets(conversationId, senderId);
        if (!targetResolution.supported()) {
            ImMetrics.typing("disabled");
            return null;
        }
        long now = System.currentTimeMillis();
        TypingSignal signal = new TypingSignal();
        signal.setConversationId(conversationId);
        signal.setSenderId(senderId);
        signal.setAction(action);
        int effectiveTtlSeconds = effectiveTtl(ttlSeconds);
        signal.setExpiresAt(action == TypingActionEnum.STOP ? now : now + effectiveTtlSeconds * 1_000L);
        if (typingStateStore.update(senderId, conversationId, action, effectiveTtlSeconds)) {
            notifyOnline(signal, targetResolution.targetUserIds(), UUID.randomUUID().toString());
            ImMetrics.typing("dispatched");
        } else {
            ImMetrics.typing("suppressed");
        }
        return signal;
    }

    private void notifyOnline(TypingSignal signal, List<String> targetUserIds, String eventId) {
        if (controlNotificationDispatcher == null) {
            return;
        }
        ServerEnvelope envelope = ServerEnvelope.of(CommandType.CHAT_TYPING, eventId, bodyOf(signal));
        for (String targetUserId : targetUserIds) {
            ControlNotificationReq request = new ControlNotificationReq();
            request.setUserId(targetUserId);
            request.setEnvelope(envelope);
            request.setDeliveryId(eventId);
            try {
                controlNotificationDispatcher.dispatch(request);
            } catch (RuntimeException ignored) {
                // 瞬时状态不做离线补偿，客户端由 expiresAt 收敛。
            }
        }
    }

    private Map<String, Object> bodyOf(TypingSignal signal) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("conversationId", signal.getConversationId());
        body.put("senderId", signal.getSenderId());
        body.put("action", signal.getAction().getCode());
        body.put("expiresAt", signal.getExpiresAt());
        return body;
    }

    private TargetResolution notificationTargets(String conversationId, String senderId) {
        if (conversationId.startsWith("s:")) {
            String[] parts = conversationId.split(":", 3);
            if (parts.length != 3) {
                return TargetResolution.unsupported();
            }
            return TargetResolution.supported(parts[1].equals(senderId) ? List.of(parts[2]) : List.of(parts[1]));
        }
        if (!conversationId.startsWith("g:") || groupMembershipFacade == null) {
            return TargetResolution.unsupported();
        }
        try {
            String groupId = conversationId.substring(2);
            if (groupMembershipFacade.loadGroupType(groupId) == GroupTypeEnum.SUPER_GROUP) {
                return TargetResolution.unsupported();
            }
            List<String> members = groupMembershipFacade.loadGroupMembers(groupId);
            if (members == null || members.isEmpty() || members.size() > maxNormalGroupMembers) {
                return TargetResolution.unsupported();
            }
            return TargetResolution.supported(
                    members.stream().filter(memberId -> !senderId.equals(memberId)).distinct().toList());
        } catch (RuntimeException ignored) {
            return TargetResolution.unsupported();
        }
    }

    private boolean isConversationMember(String userId, String conversationId) {
        if (conversationPermissionService == null) {
            return false;
        }
        try {
            ConversationPermissionRequest request = new ConversationPermissionRequest();
            request.setUserId(userId);
            request.setConversationId(conversationId);
            Object response = conversationPermissionService.check(request);
            return response instanceof PermissionCheckResult result && result.isAllowed();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private int effectiveTtl(int requestedTtlSeconds) {
        return requestedTtlSeconds <= 0 ? defaultTtlSeconds : clampTtl(requestedTtlSeconds);
    }

    private int clampTtl(int ttlSeconds) {
        return Math.max(MIN_TTL_SECONDS, Math.min(MAX_TTL_SECONDS, ttlSeconds));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record TargetResolution(boolean supported, List<String> targetUserIds) {
        private static TargetResolution supported(List<String> targetUserIds) {
            return new TargetResolution(true, targetUserIds);
        }

        private static TargetResolution unsupported() {
            return new TargetResolution(false, List.of());
        }
    }
}
