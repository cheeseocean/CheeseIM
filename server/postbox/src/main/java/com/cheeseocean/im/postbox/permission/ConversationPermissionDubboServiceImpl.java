package com.cheeseocean.im.postbox.permission;

import com.cheeseocean.im.common.api.permission.ConversationPermissionDubboService;
import com.cheeseocean.im.common.core.auth.PermissionCheckRequest;
import com.cheeseocean.im.common.core.auth.PermissionCheckResult;
import org.apache.dubbo.config.annotation.DubboService;

@DubboService
public class ConversationPermissionDubboServiceImpl implements ConversationPermissionDubboService {

    private final ConversationPermissionService conversationPermissionService;

    public ConversationPermissionDubboServiceImpl(ConversationPermissionService conversationPermissionService) {
        this.conversationPermissionService = conversationPermissionService;
    }

    @Override
    public PermissionCheckResult check(PermissionCheckRequest request) {
        return conversationPermissionService.check(request);
    }
}
