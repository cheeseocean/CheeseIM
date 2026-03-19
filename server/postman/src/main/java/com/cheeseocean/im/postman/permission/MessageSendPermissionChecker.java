package com.cheeseocean.im.postman.permission;

import com.cheeseocean.im.common.api.permission.ConversationPermissionDubboService;
import com.cheeseocean.im.common.enums.ConversationAction;
import com.cheeseocean.im.common.model.auth.PermissionCheckRequest;
import com.cheeseocean.im.common.model.auth.PermissionCheckResult;
import com.cheeseocean.im.postman.model.SendMessageCommand;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

@Component
public class MessageSendPermissionChecker {

    @DubboReference(check = false)
    private ConversationPermissionDubboService conversationPermissionDubboService;

    public PermissionCheckResult check(SendMessageCommand command) {
        PermissionCheckRequest request = new PermissionCheckRequest();
        request.setTenantId(command.getTenantId());
        request.setUserId(command.getSenderUserId());
        request.setSessionId(command.getSenderSessionId());
        request.setDeviceId(command.getSenderDeviceId());
        request.setConversationId(command.getConversationId());
        request.setAction(ConversationAction.SEND.name());
        return conversationPermissionDubboService.check(request);
    }
}
