package com.cheeseocean.im.postbox.permission;

import com.cheeseocean.im.common.model.auth.PermissionCheckRequest;
import com.cheeseocean.im.common.model.auth.PermissionCheckResult;
import com.cheeseocean.im.postbox.history.MessageIdMappingDoc;
import com.cheeseocean.im.postbox.history.MessageIdMappingRepository;
import org.springframework.stereotype.Service;

@Service
public class HistoryAccessService {

    private final ConversationPermissionService conversationPermissionService;
    private final MessageIdMappingRepository messageIdMappingRepository;

    public HistoryAccessService(ConversationPermissionService conversationPermissionService,
                                MessageIdMappingRepository messageIdMappingRepository) {
        this.conversationPermissionService = conversationPermissionService;
        this.messageIdMappingRepository = messageIdMappingRepository;
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
        MessageIdMappingDoc message = messageIdMappingRepository.findByServerMsgId(messageId).orElse(null);
        if (message == null) {
            return PermissionCheckResult.deny("MESSAGE_NOT_FOUND", "message not found");
        }
        return checkConversationRead(tenantId, userId, message.getConversationId());
    }
}
