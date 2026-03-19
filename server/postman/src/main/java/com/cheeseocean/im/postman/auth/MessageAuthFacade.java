package com.cheeseocean.im.postman.auth;

import com.cheeseocean.im.common.dto.DeliveryCommand;
import com.cheeseocean.im.common.model.auth.PermissionCheckResult;
import com.cheeseocean.im.postman.model.SendMessageCommand;
import com.cheeseocean.im.postman.permission.MessageSendPermissionChecker;
import org.springframework.stereotype.Component;

@Component
public class MessageAuthFacade {

    private final SenderIdentityResolver senderIdentityResolver;
    private final MessageSendPermissionChecker permissionChecker;

    public MessageAuthFacade(SenderIdentityResolver senderIdentityResolver,
                             MessageSendPermissionChecker permissionChecker) {
        this.senderIdentityResolver = senderIdentityResolver;
        this.permissionChecker = permissionChecker;
    }

    public void authorizeSend(DeliveryCommand command) {
        SendMessageCommand resolved = senderIdentityResolver.resolve(command);
        PermissionCheckResult result = permissionChecker.check(resolved);
        if (result == null || !result.isAllowed()) {
            String reason = result == null ? "SEND_DENIED" : result.getMessage();
            throw new IllegalStateException(reason);
        }
    }
}
