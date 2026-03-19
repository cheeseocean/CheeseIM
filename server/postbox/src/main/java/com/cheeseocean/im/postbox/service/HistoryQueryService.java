package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.api.permission.ConversationPermissionDubboService;
import com.cheeseocean.im.common.enums.ConversationAction;
import com.cheeseocean.im.common.model.auth.PermissionCheckRequest;
import com.cheeseocean.im.common.model.auth.PermissionCheckResult;
import com.cheeseocean.im.common.model.auth.SessionPrincipal;
import com.cheeseocean.im.postbox.api.HistoryMessageResponse;
import com.cheeseocean.im.postbox.entity.InboxDocument;
import com.cheeseocean.im.postbox.entity.MessageDocument;
import com.cheeseocean.im.postbox.repository.InboxDocumentRepository;
import com.cheeseocean.im.postbox.repository.MessageDocumentRepository;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class HistoryQueryService {

    private final InboxDocumentRepository inboxDocumentRepository;
    private final MessageDocumentRepository messageDocumentRepository;

    @DubboReference(check = false)
    private ConversationPermissionDubboService conversationPermissionDubboService;

    public HistoryQueryService(InboxDocumentRepository inboxDocumentRepository,
                               MessageDocumentRepository messageDocumentRepository) {
        this.inboxDocumentRepository = inboxDocumentRepository;
        this.messageDocumentRepository = messageDocumentRepository;
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

        List<InboxDocument> inboxItems = inboxDocumentRepository
                .findByUserIdAndConversationIdOrderBySequenceDesc(session.getUserId(), conversationId)
                .stream()
                .limit(limit)
                .toList();

        Map<String, MessageDocument> messageById = messageDocumentRepository.findAllById(
                        inboxItems.stream().map(InboxDocument::getServerMsgId).toList())
                .stream()
                .collect(Collectors.toMap(MessageDocument::getServerMsgId, Function.identity()));

        return inboxItems.stream()
                .map(inbox -> toResponse(inbox, messageById.get(inbox.getServerMsgId())))
                .filter(item -> item != null)
                .sorted(Comparator.comparing(HistoryMessageResponse::getSequence).reversed())
                .toList();
    }

    private HistoryMessageResponse toResponse(InboxDocument inbox, MessageDocument message) {
        if (message == null) {
            return null;
        }
        HistoryMessageResponse response = new HistoryMessageResponse();
        response.setServerMsgId(message.getServerMsgId());
        response.setClientMsgId(message.getClientMsgId());
        response.setConversationId(message.getConversationId());
        response.setSenderId(message.getSenderId());
        response.setReceiverId(message.getReceiverId());
        response.setContent(message.getContent());
        response.setContentType(message.getContentType());
        response.setSequence(inbox.getSequence());
        response.setCreatedAt(message.getCreatedAt());
        return response;
    }
}
