package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.api.permission.ConversationPermissionDubboService;
import com.cheeseocean.im.common.api.permission.ConversationPermissionRequest;
import com.cheeseocean.im.common.api.session.SessionPrincipal;
import com.cheeseocean.im.common.core.auth.PermissionCheckResult;
import com.cheeseocean.im.postbox.history.MessageBlockDoc;
import com.cheeseocean.im.postbox.history.MessageSlot;
import com.cheeseocean.im.postbox.model.HistoryMessage;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.rpc.RpcException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
            return List.of();
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
