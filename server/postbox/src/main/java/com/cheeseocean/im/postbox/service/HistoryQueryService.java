package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.api.permission.ConversationPermissionDubboService;
import com.cheeseocean.im.common.api.permission.ConversationPermissionRequest;
import com.cheeseocean.im.common.api.session.SessionPrincipal;
import com.cheeseocean.im.common.api.message.MessageHistoryQueryService;
import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.dto.message.HistoryMessage;
import com.cheeseocean.im.common.api.enums.ContentType;
import com.cheeseocean.im.common.api.enums.MessageSource;
import com.cheeseocean.im.common.api.enums.MessageStatus;
import com.cheeseocean.im.common.api.enums.PlatformType;
import com.cheeseocean.im.common.api.enums.ChatType;
import com.cheeseocean.im.common.api.permission.PermissionCheckResult;
import com.cheeseocean.im.common.core.history.MessageHistoryRepository;
import com.cheeseocean.im.common.core.history.document.MessageBlockDoc;
import com.cheeseocean.im.common.core.history.document.MessageSlot;
import com.cheeseocean.im.common.core.history.document.MessageMutationDoc;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.apache.dubbo.rpc.RpcException;
import org.bson.types.Binary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * CheeseBox 历史消息查询服务。
 *
 * @author xxxcrel
 */
@Service
@DubboService
public class HistoryQueryService implements MessageHistoryQueryService {

    private static final int DEFAULT_HISTORY_LIMIT = 50;
    private static final int MAX_HISTORY_LIMIT = 200;
    private static final int MAX_RECENT_BLOCK_WINDOWS = 16;
    private static final int PERMISSION_CACHE_TTL_MILLIS = 30_000;

    private final MessageHistoryRepository messageHistoryRepository;
    private final MessagePreviewResolver messagePreviewResolver;
    private final ConcurrentMap<PermissionCacheKey, PermissionCacheValue> permissionCache = new ConcurrentHashMap<>();

    @DubboReference(check = false)
    private ConversationPermissionDubboService conversationPermissionDubboService;

    public HistoryQueryService(MessageHistoryRepository messageHistoryRepository, MessagePreviewResolver messagePreviewResolver) {
        this.messageHistoryRepository = messageHistoryRepository;
        this.messagePreviewResolver = messagePreviewResolver;
    }

    /**
     * 读取最近一页历史消息。
     */
    @Override
    public List<HistoryMessage> getConversationMessages(SessionPrincipal session, String conversationId, int limit) {
        if (!allow(session, conversationId)) {
            return new ArrayList<>();
        }
        int effectiveLimit = effectiveHistoryLimit(limit);
        List<MessageBlockDoc> blocks = findRecentBlocks(conversationId, effectiveLimit);

        List<MessageSlot> slots = new ArrayList<>();
        for (MessageBlockDoc block : blocks) {
            if (block == null || block.getMessages() == null) {
                continue;
            }
            for (MessageSlot slot : block.getMessages()) {
                if (slot != null) {
                    slots.add(slot);
                }
            }
        }
        slots.sort(Comparator.comparing(MessageSlot::getSeq, Comparator.nullsLast(Long::compareTo)).reversed());
        Map<String, MessageMutationDoc> mutations = findRevokedMutations(
                slots.stream().map(MessageSlot::getServerMsgId).toList());

        List<HistoryMessage> responses = new ArrayList<>();
        for (MessageSlot slot : slots) {
            responses.add(toHistoryMessage(slot, session.getUserId(), mutations.get(slot.getServerMsgId())));
            if (responses.size() >= effectiveLimit) {
                break;
            }
        }
        return responses;
    }

    private List<MessageBlockDoc> findRecentBlocks(String conversationId, int limit) {
        if (conversationId == null || conversationId.isBlank()) {
            return new ArrayList<>();
        }
        return messageHistoryRepository.findRecentBlocks(conversationId, limit, MAX_RECENT_BLOCK_WINDOWS);
    }

    private int effectiveHistoryLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_HISTORY_LIMIT;
        }
        return Math.min(limit, MAX_HISTORY_LIMIT);
    }

    /**
     * 按 seq 区间读取消息，用于断线重连和 gap repair。
     *
     * <p>此方法只负责历史块查询，不处理会话权限校验。
     */
    @Override
    public List<Message> pullMessagesBySeqRange(String conversationId, long beginSeq, long endSeq, int limit) {
        if (conversationId == null || conversationId.isBlank()) {
            return new ArrayList<>();
        }
        if (beginSeq <= 0 || endSeq <= 0 || endSeq < beginSeq) {
            return new ArrayList<>();
        }
        int effectiveLimit = limit <= 0 ? Integer.MAX_VALUE : limit;
        List<MessageBlockDoc> blocks = messageHistoryRepository.findBlocksBySeqRange(conversationId, beginSeq, endSeq);
        if (blocks == null || blocks.isEmpty()) {
            return new ArrayList<>();
        }

        List<Message> messages = new ArrayList<>();
        boolean limitReached = false;
        for (MessageBlockDoc block : blocks) {
            if (block == null || block.getMessages() == null) {
                continue;
            }
            for (MessageSlot slot : block.getMessages()) {
                if (slot == null || slot.getSeq() == null) {
                    continue;
                }
                if (slot.getSeq() < beginSeq || slot.getSeq() > endSeq) {
                    continue;
                }
                messages.add(toMessage(slot));
                if (messages.size() >= effectiveLimit) {
                    limitReached = true;
                    break;
                }
            }
            if (limitReached) {
                break;
            }
        }
        applyMutations(messages);
        return messages;
    }

    private HistoryMessage toHistoryMessage(MessageSlot slot,
                                            String viewerUserId,
                                            MessageMutationDoc mutation) {
        MessagePreviewResolver.Preview preview = messagePreviewResolver.resolve(slot, viewerUserId);
        HistoryMessage response = new HistoryMessage();
        response.setSequence(slot.getSeq());
        response.setServerMsgId(slot.getServerMsgId());
        response.setSenderId(slot.getSenderId());
        response.setSenderName(slot.getSenderName() == null ? slot.getSenderId() : slot.getSenderName());
        response.setContent(preview.text());
        response.setPreviewType(preview.type());
        response.setSendTime(slot.getSendTime());
        if (mutation != null) {
            response.setRevoked(true);
            response.setContent("消息已撤回");
            response.setPreviewType(com.cheeseocean.im.common.api.enums.MessagePreviewType.REVOKE);
            response.setRevokeOperatorUserId(mutation.getOperatorUserId());
            response.setRevokeOperatorName(mutation.getOperatorName());
            response.setRevokedAt(mutation.getCreatedAt() == null ? null : mutation.getCreatedAt().toEpochMilli());
            response.setMutationVersion(mutation.getMutationVersion());
        }
        return response;
    }

    private void applyMutations(List<Message> messages) {
        Map<String, MessageMutationDoc> mutations = findRevokedMutations(
                messages.stream().map(Message::getServerMsgId).toList());
        for (Message message : messages) {
            MessageMutationDoc mutation = mutations.get(message.getServerMsgId());
            if (mutation == null) {
                continue;
            }
            message.setStatus(MessageStatus.REVOKED);
            message.setContent(null);
            Map<String, String> attributes = message.getAttributes() == null
                    ? new LinkedHashMap<>() : new LinkedHashMap<>(message.getAttributes());
            attributes.put("mutationType", "REVOKED");
            attributes.put("mutationVersion", String.valueOf(mutation.getMutationVersion()));
            attributes.put("revokedAt", String.valueOf(mutation.getCreatedAt()));
            attributes.put("operatorUserId", Objects.toString(mutation.getOperatorUserId(), ""));
            message.setAttributes(attributes);
        }
    }

    private Map<String, MessageMutationDoc> findRevokedMutations(List<String> serverMsgIds) {
        Set<String> ids = new HashSet<>();
        for (String serverMsgId : serverMsgIds) {
            if (serverMsgId != null && !serverMsgId.isBlank()) {
                ids.add(serverMsgId + ":REVOKED");
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<MessageMutationDoc> mutations = messageHistoryRepository.findRevokedMutations(serverMsgIds);
        Map<String, MessageMutationDoc> byServerMsgId = new HashMap<>();
        for (MessageMutationDoc mutation : mutations) {
            if (mutation != null && mutation.getServerMsgId() != null) {
                byServerMsgId.put(mutation.getServerMsgId(), mutation);
            }
        }
        return byServerMsgId;
    }

    private Message toMessage(MessageSlot slot) {
        Message message = new Message();
        message.setSeq(slot.getSeq());
        message.setClientMsgId(slot.getClientMsgId());
        message.setServerMsgId(slot.getServerMsgId());
        message.setSenderId(slot.getSenderId());
        message.setSenderNickName(slot.getSenderName());
        message.setReceiverId(firstNonBlank(slot.getReceiverId(), slot.getRecvId()));
        message.setGroupId(slot.getGroupId());
        message.setChatType(fromCode(slot.getSessionType(), ChatType::fromCode));
        message.setContentType(fromCode(slot.getContentType(), ContentType::fromCode));
        message.setContent(toBytes(slot.getContent()));
        message.setSendTime(slot.getSendTime());
        message.setCreateTime(slot.getCreateTime());
        message.setStatus(fromCode(slot.getStatus(), MessageStatus::fromCode));
        message.setPlatformType(PlatformType.fromCode(slot.getPlatformType()));
        message.setUniqueId(slot.getUniqueId());
        message.setSource(fromCode(slot.getSource(), MessageSource::fromCode));
        message.setOptions(slot.getOptions());
        message.setAttributes(slot.getAttributes() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(slot.getAttributes()));
        return message;
    }

    private byte[] toBytes(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] bytes) {
            return bytes;
        }
        if (value instanceof Binary binary) {
            return binary.getData();
        }
        if (value instanceof String text) {
            return text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        return null;
    }

    private String firstNonBlank(String primary, String secondary) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return secondary;
    }

    private <T> T fromCode(Integer code, java.util.function.IntFunction<T> mapper) {
        if (code == null) {
            return null;
        }
        try {
            return mapper.apply(code);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean allow(SessionPrincipal session, String conversationId) {
        PermissionCacheKey cacheKey = PermissionCacheKey.from(session, conversationId);
        if (cacheKey == null) {
            return false;
        }
        if (conversationPermissionDubboService == null) {
            return cachedDecision(cacheKey);
        }
        ConversationPermissionRequest request = new ConversationPermissionRequest();
        request.setTenantId(session.getTenantId());
        request.setUserId(session.getUserId());
        request.setConversationId(conversationId);
        try {
            Object raw = conversationPermissionDubboService.check(request);
            PermissionCheckResult result = raw instanceof PermissionCheckResult permissionCheckResult
                    ? permissionCheckResult
                    : null;
            if (result == null) {
                return cachedDecision(cacheKey);
            }
            cacheDecision(cacheKey, result.isAllowed());
            return result.isAllowed();
        } catch (RpcException ignored) {
            return cachedDecision(cacheKey);
        } catch (RuntimeException ignored) {
            return cachedDecision(cacheKey);
        }
    }

    private void cacheDecision(PermissionCacheKey cacheKey, boolean allowed) {
        permissionCache.put(cacheKey, new PermissionCacheValue(allowed, System.currentTimeMillis() + PERMISSION_CACHE_TTL_MILLIS));
    }

    private boolean cachedDecision(PermissionCacheKey cacheKey) {
        PermissionCacheValue value = permissionCache.get(cacheKey);
        long now = System.currentTimeMillis();
        if (value == null || value.expireAtMillis() <= now) {
            permissionCache.remove(cacheKey);
            return false;
        }
        return value.allowed();
    }

    private record PermissionCacheKey(String tenantId, String userId, String conversationId) {

        private static PermissionCacheKey from(SessionPrincipal session, String conversationId) {
            if (session == null || session.getUserId() == null || session.getUserId().isBlank()
                    || conversationId == null || conversationId.isBlank()) {
                return null;
            }
            return new PermissionCacheKey(
                    Objects.toString(session.getTenantId(), ""),
                    session.getUserId(),
                    conversationId);
        }
    }

    private record PermissionCacheValue(boolean allowed, long expireAtMillis) {
    }
}
