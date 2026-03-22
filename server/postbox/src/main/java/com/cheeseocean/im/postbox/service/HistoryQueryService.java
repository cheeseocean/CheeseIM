package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.api.permission.ConversationPermissionDubboService;
import com.cheeseocean.im.common.core.auth.PermissionCheckRequest;
import com.cheeseocean.im.common.core.auth.PermissionCheckResult;
import com.cheeseocean.im.common.core.auth.SessionPrincipal;
import com.cheeseocean.im.common.core.enums.ConversationAction;
import com.cheeseocean.im.postbox.api.HistoryMessageResponse;
import com.cheeseocean.im.postbox.history.MessageBlockDoc;
import com.cheeseocean.im.postbox.history.MessageSlot;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class HistoryQueryService {

    private final MongoTemplate mongoTemplate;

    @DubboReference(check = false)
    private ConversationPermissionDubboService conversationPermissionDubboService;

    public HistoryQueryService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public List<HistoryMessageResponse> getConversationMessages(SessionPrincipal session, String conversationId, int limit) {
        PermissionCheckRequest request = new PermissionCheckRequest();
        request.setTenantId(session.getTenantId());
        request.setUserId(session.getUserId());
        request.setSessionId(session.getSessionId());
        request.setDeviceId(session.getDeviceId());
        request.setConversationId(conversationId);
        request.setAction(ConversationAction.READ.name());
        PermissionCheckResult permission = conversationPermissionDubboService.check(request);
        if (permission == null || !permission.isAllowed()) {
            throw new IllegalStateException(permission == null ? "history access denied" : permission.getMessage());
        }

        Query query = Query.query(Criteria.where("conversationId").is(conversationId))
                .with(Sort.by(Sort.Direction.DESC, "blockNo"));

        int blockFetchSize = Math.max(1, (limit + 99) / 100);
        query.limit(blockFetchSize);

        return mongoTemplate.find(query, MessageBlockDoc.class).stream()
                .flatMap(block -> block.getMessages().stream())
                .filter(Objects::nonNull)
                .filter(slot -> slot.getSeq() != null)
                .sorted((left, right) -> Long.compare(right.getSeq(), left.getSeq()))
                .limit(limit)
                .map(slot -> toResponse(conversationId, slot))
                .toList();
    }

    private HistoryMessageResponse toResponse(String conversationId, MessageSlot message) {
        HistoryMessageResponse response = new HistoryMessageResponse();
        response.setServerMsgId(message.getServerMsgId());
        response.setClientMsgId(message.getClientMsgId());
        response.setConversationId(conversationId);
        response.setSenderId(message.getSenderId());
        response.setReceiverId(message.getRecvId());
        response.setContent(message.getContent());
        response.setContentType(message.getContentType());
        response.setSequence(message.getSeq());
        response.setCreatedAt(message.getSendTime() == null ? null : java.time.Instant.ofEpochMilli(message.getSendTime()));
        return response;
    }
}
