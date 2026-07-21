package com.cheeseocean.im.postmaster.mutation;

import com.cheeseocean.im.common.api.dto.message.MessageMutationResult;
import com.cheeseocean.im.common.api.business.domain.ConversationControlEvent;
import com.cheeseocean.im.common.api.dto.message.MessageMutationSyncResult;
import com.cheeseocean.im.common.api.dto.dispatch.ControlNotificationReq;
import com.cheeseocean.im.common.api.enums.CommandType;
import com.cheeseocean.im.common.api.enums.ControlEventTypeEnum;
import com.cheeseocean.im.common.api.enums.GroupTypeEnum;
import com.cheeseocean.im.common.api.enums.MessageMutationTypeEnum;
import com.cheeseocean.im.common.api.message.MessageMutationService;
import com.cheeseocean.im.common.api.permission.ConversationPermissionService;
import com.cheeseocean.im.common.api.permission.ConversationPermissionRequest;
import com.cheeseocean.im.common.api.permission.PermissionCheckResult;
import com.cheeseocean.im.common.core.business.repository.ConversationControlEventRepository;
import com.cheeseocean.im.common.core.history.MessageHistoryRepository;
import com.cheeseocean.im.common.core.history.model.MessageIdMapping;
import com.cheeseocean.im.common.core.history.model.MessageMutation;
import com.cheeseocean.im.postmaster.service.GroupMembershipFacade;
import com.cheeseocean.im.common.api.protocol.ServerEnvelope;
import com.cheeseocean.im.common.api.rpc.ControlNotificationDispatcher;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Value;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 撤回 mutation 的服务端真相实现。
 *
 * <p>在写 overlay 前以服务端 mapping 验证目标消息、会话、发送者和服务端发送时间，
 * 因此客户端无法通过伪造时间或会话 ID 扩大撤回权限。
 */
@DubboService(retries = 0)
public class MessageMutationServiceImpl implements MessageMutationService {

    private static final int DEFAULT_SYNC_LIMIT = 100;
    private static final int MAX_SYNC_LIMIT = 200;
    private static final String REVOKED_SUFFIX = ":REVOKED";

    private final MessageHistoryRepository messageHistoryRepository;
    private final long revokeWindowMillis;
    private final GroupMembershipFacade groupMembershipFacade;
    private final ConversationControlEventRepository controlEventRepository;
    private final ObjectMapper objectMapper;

    @DubboReference(check = false)
    private ConversationPermissionService conversationPermissionService;

    @DubboReference(check = false, retries = 0)
    private ControlNotificationDispatcher controlNotificationDispatcher;

    public MessageMutationServiceImpl(MessageHistoryRepository messageHistoryRepository,
                                      @Value("${cheeseim.message-mutation.revoke-window-seconds:120}") long revokeWindowSeconds,
                                      GroupMembershipFacade groupMembershipFacade,
                                      ConversationControlEventRepository controlEventRepository,
                                      ObjectMapper objectMapper) {
        this.messageHistoryRepository = messageHistoryRepository;
        this.revokeWindowMillis = Math.max(1L, revokeWindowSeconds) * 1_000L;
        this.groupMembershipFacade = groupMembershipFacade;
        this.controlEventRepository = controlEventRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public MessageMutationResult revoke(String operatorUserId, String conversationId,
                                        String serverMsgId, String reason) {
        if (isBlank(operatorUserId) || isBlank(conversationId) || isBlank(serverMsgId)) {
            return MessageMutationResult.rejected("INVALID_ARGUMENT", "撤回参数不完整");
        }
        if (!isConversationMember(operatorUserId, conversationId)) {
            return MessageMutationResult.rejected("CONVERSATION_FORBIDDEN", "无会话访问权限");
        }

        MessageIdMapping mapping = messageHistoryRepository.findMappingByServerMessageId(serverMsgId);
        if (mapping == null) {
            return MessageMutationResult.rejected("MESSAGE_NOT_FOUND", "消息不存在");
        }
        if (!conversationId.equals(mapping.getConversationId())) {
            return MessageMutationResult.rejected("CONVERSATION_MISMATCH", "目标消息不属于当前会话");
        }
        if (!operatorUserId.equals(mapping.getSenderId())) {
            return MessageMutationResult.rejected("REVOKE_FORBIDDEN", "只能撤回自己发送的消息");
        }
        if (mapping.getSendTime() == null || System.currentTimeMillis() - mapping.getSendTime() > revokeWindowMillis) {
            return MessageMutationResult.rejected("REVOKE_WINDOW_EXPIRED", "已超过撤回时间窗口");
        }

        String mutationId = serverMsgId + REVOKED_SUFFIX;
        MessageMutation existing = messageHistoryRepository.findMutationById(mutationId);
        if (existing != null) {
            return notifyOnline(toResult(existing));
        }

        Instant now = Instant.now();
        MessageMutation pending = new MessageMutation();
        pending.setId(mutationId);
        pending.setServerMsgId(serverMsgId);
        pending.setConversationId(conversationId);
        pending.setMutationType(MessageMutationTypeEnum.REVOKED.getCode());
        pending.setOperatorUserId(operatorUserId);
        pending.setOperatorName(operatorUserId);
        pending.setTargetSenderId(mapping.getSenderId());
        pending.setTargetSenderName(mapping.getSenderId());
        pending.setReason(normalizeReason(reason));
        pending.setMutationVersion(now.toEpochMilli());
        pending.setCreatedAt(now);
        try {
            MessageMutation mutation = messageHistoryRepository.upsertMutation(pending);
            return notifyOnline(toResult(mutation));
        } catch (DuplicateKeyException ignored) {
            MessageMutation mutation = messageHistoryRepository.findMutationById(mutationId);
            return mutation == null
                    ? MessageMutationResult.rejected("MUTATION_RETRY", "撤回写入冲突，请重试")
                    : notifyOnline(toResult(mutation));
        }
    }

    @Override
    public MessageMutationSyncResult sync(String userId, String conversationId,
                                          long afterCreatedAt, String afterMutationId, int limit) {
        if (isBlank(userId) || isBlank(conversationId)) {
            return MessageMutationSyncResult.rejected("INVALID_ARGUMENT", "同步参数不完整");
        }
        if (!isConversationMember(userId, conversationId)) {
            return MessageMutationSyncResult.rejected("CONVERSATION_FORBIDDEN", "无会话访问权限");
        }

        long cursorMillis = Math.max(0L, afterCreatedAt);
        Instant cursorTime = Instant.ofEpochMilli(cursorMillis);
        int pageSize = effectiveLimit(limit);
        List<MessageMutation> docs = messageHistoryRepository.findMutationsAfter(
                conversationId, cursorTime, afterMutationId, pageSize + 1);

        MessageMutationSyncResult result = new MessageMutationSyncResult();
        result.setSuccess(true);
        result.setHasMore(docs.size() > pageSize);
        int returned = Math.min(docs.size(), pageSize);
        for (int index = 0; index < returned; index++) {
            MessageMutation doc = docs.get(index);
            result.getMutations().add(toResult(doc));
        }
        if (returned > 0) {
            MessageMutation last = docs.get(returned - 1);
            result.setNextCreatedAt(last.getCreatedAt().toEpochMilli());
            result.setNextMutationId(last.getId());
        } else {
            result.setNextCreatedAt(cursorMillis);
            result.setNextMutationId(afterMutationId);
        }
        return result;
    }

    private MessageMutationResult toResult(MessageMutation mutation) {
        MessageMutationResult result = new MessageMutationResult();
        result.setSuccess(true);
        result.setMutationId(mutation.getId());
        result.setConversationId(mutation.getConversationId());
        result.setServerMsgId(mutation.getServerMsgId());
        result.setOperatorUserId(mutation.getOperatorUserId());
        result.setOperatorName(mutation.getOperatorName());
        result.setTargetSenderId(mutation.getTargetSenderId());
        result.setTargetSenderName(mutation.getTargetSenderName());
        result.setReason(mutation.getReason());
        result.setMutationVersion(mutation.getMutationVersion() == null ? 0L : mutation.getMutationVersion());
        result.setRevokedAt(mutation.getCreatedAt() == null ? 0L : mutation.getCreatedAt().toEpochMilli());
        return result;
    }

    private MessageMutationResult notifyOnline(MessageMutationResult result) {
        if (result == null || !result.isSuccess()) {
            return result;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("conversationId", result.getConversationId());
        body.put("serverMsgId", result.getServerMsgId());
        body.put("operatorUserId", result.getOperatorUserId());
        body.put("operatorName", result.getOperatorName());
        body.put("targetSenderId", result.getTargetSenderId());
        body.put("targetSenderName", result.getTargetSenderName());
        body.put("revokedAt", result.getRevokedAt());
        body.put("mutationVersion", result.getMutationVersion());
        List<String> targets = notificationTargets(result.getConversationId());
        List<ConversationControlEvent> events = appendControlEvents(result, targets, body);
        if (controlNotificationDispatcher == null) {
            return result;
        }
        if (events.isEmpty()) {
            dispatchPartition(targets, "revoke:" + result.getMutationId(), body);
        } else {
            events.forEach(event -> dispatchPartition(event.getTargetUserIds(), event.getEventId(), body));
        }
        return result;
    }

    private void dispatchPartition(List<String> targets, String deliveryId, Map<String, Object> body) {
        ServerEnvelope envelope = ServerEnvelope.of(CommandType.CHAT_REVOKE, deliveryId, body);
        for (String userId : targets) {
            ControlNotificationReq request = new ControlNotificationReq();
            request.setUserId(userId);
            request.setEnvelope(envelope);
            request.setDeliveryId(deliveryId);
            try {
                controlNotificationDispatcher.dispatch(request);
            } catch (RuntimeException ignored) {
                // mutation 已是持久化真相，离线端由 mutation 增量同步收敛。
            }
        }
    }

    private List<ConversationControlEvent> appendControlEvents(MessageMutationResult result,
                                                                List<String> targets,
                                                                Map<String, Object> body) {
        if (controlEventRepository == null || objectMapper == null || targets.isEmpty()) {
            return List.of();
        }
        try {
            ConversationControlEvent event = new ConversationControlEvent();
            event.setEventId("revoke:" + result.getMutationId());
            event.setConversationId(result.getConversationId());
            event.setType(ControlEventTypeEnum.MESSAGE_REVOKED);
            event.setTargetUserIds(targets);
            event.setPayload(objectMapper.writeValueAsString(body));
            event.setExpiresAt(System.currentTimeMillis() + 180L * 24 * 60 * 60 * 1000);
            List<ConversationControlEvent> events = controlEventRepository.appendPartitioned(event);
            return events == null ? List.of() : events;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<String> notificationTargets(String conversationId) {
        if (conversationId == null) {
            return List.of();
        }
        if (conversationId.startsWith("s:")) {
            String[] parts = conversationId.split(":", 3);
            return parts.length == 3 ? List.of(parts[1], parts[2]) : List.of();
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
            return members == null ? List.of() : new ArrayList<>(members);
        } catch (RuntimeException ignored) {
            return List.of();
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

    private int effectiveLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_SYNC_LIMIT;
        }
        return Math.min(limit, MAX_SYNC_LIMIT);
    }

    private String normalizeReason(String reason) {
        return isBlank(reason) ? null : reason.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
