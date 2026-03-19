package com.cheeseocean.im.postbox.permission;

import com.cheeseocean.im.postbox.entity.MessageDocument;
import com.cheeseocean.im.postbox.repository.MessageDocumentRepository;
import com.cheeseocean.im.common.model.auth.PermissionCheckRequest;
import com.cheeseocean.im.common.model.auth.PermissionCheckResult;
import org.springframework.stereotype.Service;

@Service
public class HistoryAccessService {

    private final ConversationPermissionService conversationPermissionService;
    private final MessageDocumentRepository messageDocumentRepository;

    public HistoryAccessService(ConversationPermissionService conversationPermissionService,
                                MessageDocumentRepository messageDocumentRepository) {
        this.conversationPermissionService = conversationPermissionService;
        this.messageDocumentRepository = messageDocumentRepository;
    }

    public PermissionCheckResult checkConversationRead(String tenantId, String userId, String conversationId) {
        PermissionCheckRequest request = new PermissionCheckRequest();
        request.setTenantId(tenantId);
        request.setUserId(userId);
        request.setConversationId(conversationId);
        request.setAction("READ");
        return conversationPermissionService.check(request);
    }

    public PermissionCheckResult checkMessageRead(String tenantId, String userId, String messageId) {
        MessageDocument message = messageDocumentRepository.findByServerMsgId(messageId);
        if (message == null) {
            return PermissionCheckResult.deny("MESSAGE_NOT_FOUND", "message not found");
        }
        return checkConversationRead(tenantId, userId, message.getConversationId());
    }
}
