package com.cheeseocean.im.postmaster.typing;

import com.cheeseocean.im.common.api.business.domain.ConversationControlEvent;
import com.cheeseocean.im.common.api.conversation.TypingStateService;
import com.cheeseocean.im.common.api.dto.conversation.TypingSignal;
import com.cheeseocean.im.common.api.dto.dispatch.ControlNotificationReq;
import com.cheeseocean.im.common.api.enums.CommandType;
import com.cheeseocean.im.common.api.enums.ControlEventTypeEnum;
import com.cheeseocean.im.common.api.enums.GroupTypeEnum;
import com.cheeseocean.im.common.api.enums.TypingActionEnum;
import com.cheeseocean.im.common.api.permission.ConversationPermissionDubboService;
import com.cheeseocean.im.common.api.permission.ConversationPermissionRequest;
import com.cheeseocean.im.common.api.protocol.ServerEnvelope;
import com.cheeseocean.im.common.api.rpc.ControlNotificationDispatcher;
import com.cheeseocean.im.common.core.auth.PermissionCheckResult;
import com.cheeseocean.im.common.core.business.repository.ConversationControlEventRepository;
import com.cheeseocean.im.postmaster.service.GroupMembershipFacade;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Value;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final GroupMembershipFacade groupMembershipFacade;
    private final ConversationControlEventRepository conversationControlEventRepository;
    private final int defaultTtlSeconds;

    @DubboReference(check = false)
    private ConversationPermissionDubboService conversationPermissionDubboService;

    @DubboReference(check = false, retries = 0)
    private ControlNotificationDispatcher controlNotificationDispatcher;

    public TypingStateServiceImpl(GroupMembershipFacade groupMembershipFacade,
                                  ConversationControlEventRepository conversationControlEventRepository,
                                  @Value("${cheeseim.typing.default-ttl-seconds:4}") int defaultTtlSeconds) {
        this.groupMembershipFacade = groupMembershipFacade;
        this.conversationControlEventRepository = conversationControlEventRepository;
        this.defaultTtlSeconds = clampTtl(defaultTtlSeconds);
    }

    @Override
    public TypingSignal publish(String senderId, String conversationId, TypingActionEnum action, int ttlSeconds) {
        if (isBlank(senderId) || isBlank(conversationId) || action == null || !isConversationMember(senderId, conversationId)) {
            return null;
        }
        long now = System.currentTimeMillis();
        TypingSignal signal = new TypingSignal();
        signal.setConversationId(conversationId);
        signal.setSenderId(senderId);
        signal.setAction(action);
        int effectiveTtlSeconds = effectiveTtl(ttlSeconds);
        signal.setExpiresAt(action == TypingActionEnum.STOP ? now : now + effectiveTtlSeconds * 1_000L);
        List<String> targetUserIds = notificationTargets(conversationId, senderId);
        ConversationControlEvent event = appendControlEvent(signal, targetUserIds, now + effectiveTtlSeconds * 1_000L);
        if (event != null) {
            notifyOnline(signal, targetUserIds, event.getEventId());
        }
        return signal;
    }

    private ConversationControlEvent appendControlEvent(TypingSignal signal, List<String> targetUserIds,
                                                        long eventExpiresAt) {
        if (conversationControlEventRepository == null || targetUserIds.isEmpty()) {
            return null;
        }
        ConversationControlEvent event = new ConversationControlEvent();
        event.setConversationId(signal.getConversationId());
        event.setType(signal.getAction() == TypingActionEnum.START
                ? ControlEventTypeEnum.TYPING_STARTED : ControlEventTypeEnum.TYPING_STOPPED);
        event.setTargetUserIds(targetUserIds);
        event.setPayload(serializeBody(signal));
        event.setExpiresAt(eventExpiresAt);
        return conversationControlEventRepository.append(event);
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
            // 与 postman outbox 补偿使用同一 eventId，避免首次直推成功后又向同一连接重复投递。
            request.setDeliveryId(eventId);
            try {
                controlNotificationDispatcher.dispatch(request);
            } catch (RuntimeException ignored) {
                // 瞬时状态不做离线补偿，客户端由 expiresAt 收敛。
            }
        }
    }

    private String serializeBody(TypingSignal signal) {
        try {
            return OBJECT_MAPPER.writeValueAsString(bodyOf(signal));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("输入中控制事件序列化失败", exception);
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

    private List<String> notificationTargets(String conversationId, String senderId) {
        if (conversationId.startsWith("s:")) {
            String[] parts = conversationId.split(":", 3);
            if (parts.length != 3) {
                return List.of();
            }
            return parts[1].equals(senderId) ? List.of(parts[2]) : List.of(parts[1]);
        }
        if (!conversationId.startsWith("g:") || groupMembershipFacade == null) {
            return List.of();
        }
        try {
            String groupId = conversationId.substring(2);
            if (groupMembershipFacade.loadGroupType(groupId) == GroupTypeEnum.SUPER_GROUP) {
                return List.of();
            }
            List<String> members = groupMembershipFacade.loadGroupMembers(groupId);
            if (members == null || members.isEmpty()) {
                return List.of();
            }
            return members.stream().filter(memberId -> !senderId.equals(memberId)).distinct().toList();
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private boolean isConversationMember(String userId, String conversationId) {
        if (conversationPermissionDubboService == null) {
            return false;
        }
        try {
            ConversationPermissionRequest request = new ConversationPermissionRequest();
            request.setUserId(userId);
            request.setConversationId(conversationId);
            Object response = conversationPermissionDubboService.check(request);
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
}
