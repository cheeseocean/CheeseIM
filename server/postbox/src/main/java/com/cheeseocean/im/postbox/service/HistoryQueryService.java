package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.api.permission.ConversationPermissionDubboService;
import com.cheeseocean.im.common.api.permission.ConversationPermissionRequest;
import com.cheeseocean.im.common.api.session.SessionPrincipal;
import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.enums.ContentType;
import com.cheeseocean.im.common.api.enums.MessageSource;
import com.cheeseocean.im.common.api.enums.MessageStatus;
import com.cheeseocean.im.common.api.enums.PlatformType;
import com.cheeseocean.im.common.api.enums.SessionType;
import com.cheeseocean.im.common.core.auth.PermissionCheckResult;
import com.cheeseocean.im.common.core.util.BlockIndexUtil;
import com.cheeseocean.im.postbox.history.MessageBlockDoc;
import com.cheeseocean.im.postbox.history.MessageSlot;
import com.cheeseocean.im.postbox.model.HistoryMessage;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.rpc.RpcException;
import org.bson.types.Binary;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * CheeseBox 历史消息查询服务。
 *
 * @author xxxcrel
 */
@Service
public class HistoryQueryService {

    private final MongoTemplate mongoTemplate;
    private final MessagePreviewResolver messagePreviewResolver;

    @DubboReference(check = false)
    private ConversationPermissionDubboService conversationPermissionDubboService;

    public HistoryQueryService(MongoTemplate mongoTemplate, MessagePreviewResolver messagePreviewResolver) {
        this.mongoTemplate = mongoTemplate;
        this.messagePreviewResolver = messagePreviewResolver;
    }

    /**
     * 读取最近一页历史消息。
     */
    public List<HistoryMessage> getConversationMessages(SessionPrincipal session, String conversationId, int limit) {
        if (!allow(session, conversationId)) {
            return new ArrayList<>();
        }
        Query query = Query.query(Criteria.where("conversationId").is(conversationId))
                .with(Sort.by(Sort.Direction.DESC, "blockNo"));
        List<MessageBlockDoc> blocks = mongoTemplate.find(query, MessageBlockDoc.class);

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

        List<HistoryMessage> responses = new ArrayList<>();
        for (MessageSlot slot : slots) {
            responses.add(toHistoryMessage(slot, session.getUserId()));
            if (responses.size() >= limit) {
                break;
            }
        }
        return responses;
    }

    /**
     * 按 seq 区间读取消息，用于断线重连和 gap repair。
     *
     * <p>此方法只负责历史块查询，不处理会话权限校验。
     */
    public List<Message> pullMessagesBySeqRange(String conversationId, long beginSeq, long endSeq, int limit) {
        if (conversationId == null || conversationId.isBlank()) {
            return new ArrayList<>();
        }
        if (beginSeq <= 0 || endSeq <= 0 || endSeq < beginSeq) {
            return new ArrayList<>();
        }
        int effectiveLimit = limit <= 0 ? Integer.MAX_VALUE : limit;
        long beginBlock = BlockIndexUtil.blockNo(beginSeq);
        long endBlock = BlockIndexUtil.blockNo(endSeq);
        Query query = Query.query(Criteria.where("conversationId").is(conversationId)
                        .and("blockNo").gte(beginBlock).lte(endBlock))
                .with(Sort.by(Sort.Direction.ASC, "blockNo"));
        List<MessageBlockDoc> blocks = mongoTemplate.find(query, MessageBlockDoc.class);
        if (blocks == null || blocks.isEmpty()) {
            return new ArrayList<>();
        }

        List<Message> messages = new ArrayList<>();
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
                    return messages;
                }
            }
        }
        return messages;
    }

    private HistoryMessage toHistoryMessage(MessageSlot slot, String viewerUserId) {
        MessagePreviewResolver.Preview preview = messagePreviewResolver.resolve(slot, viewerUserId);
        HistoryMessage response = new HistoryMessage();
        response.setSequence(slot.getSeq());
        response.setServerMsgId(slot.getServerMsgId());
        response.setSenderId(slot.getSenderId());
        response.setSenderName(slot.getSenderName() == null ? slot.getSenderId() : slot.getSenderName());
        response.setContent(preview.text());
        response.setPreviewType(preview.type());
        response.setSendTime(slot.getSendTime());
        return response;
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
        message.setSessionType(fromCode(slot.getSessionType(), SessionType::fromCode));
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
        if (conversationPermissionDubboService == null) {
            return true;
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
            return result == null || result.isAllowed();
        } catch (RpcException ignored) {
            return true;
        }
    }
}
