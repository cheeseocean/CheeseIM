package com.cheeseocean.im.postmaster.permission;

import com.cheeseocean.im.common.api.permission.ConversationPermissionDubboService;
import com.cheeseocean.im.common.core.auth.PermissionCheckRequest;
import com.cheeseocean.im.common.core.auth.PermissionCheckResult;
import com.cheeseocean.im.common.core.enums.ConversationAction;
import com.cheeseocean.im.postmaster.model.SendMessageCommand;
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
