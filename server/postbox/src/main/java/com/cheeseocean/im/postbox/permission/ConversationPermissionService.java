package com.cheeseocean.im.postbox.permission;

import com.cheeseocean.im.common.core.auth.PermissionCheckRequest;
import com.cheeseocean.im.common.core.auth.PermissionCheckResult;
import com.cheeseocean.im.postbox.policy.ChannelPolicy;
import com.cheeseocean.im.postbox.policy.DirectChatPolicy;
import com.cheeseocean.im.postbox.policy.GroupChatPolicy;
import org.springframework.stereotype.Service;

@Service
public class ConversationPermissionService {

    private final DirectChatPolicy directChatPolicy;
    private final GroupChatPolicy groupChatPolicy;
    private final ChannelPolicy channelPolicy;

    public ConversationPermissionService(DirectChatPolicy directChatPolicy,
                                         GroupChatPolicy groupChatPolicy,
                                         ChannelPolicy channelPolicy) {
        this.directChatPolicy = directChatPolicy;
        this.groupChatPolicy = groupChatPolicy;
        this.channelPolicy = channelPolicy;
    }

    public PermissionCheckResult check(PermissionCheckRequest request) {
        if (request == null || request.getUserId() == null || request.getConversationId() == null || request.getAction() == null) {
            return PermissionCheckResult.deny("INVALID_REQUEST", "permission request invalid");
        }

        boolean allowed;
        String conversationId = request.getConversationId();
        if (conversationId.startsWith("c1:")) {
            allowed = directChatPolicy.canAccess(conversationId, request.getUserId());
        } else if (conversationId.startsWith("c2:")) {
            allowed = groupChatPolicy.canAccess(conversationId, request.getUserId());
        } else if (conversationId.startsWith("c4:")) {
            allowed = channelPolicy.canAccess(conversationId, request.getUserId());
        } else {
            allowed = false;
        }

        return allowed
                ? PermissionCheckResult.allow()
                : PermissionCheckResult.deny("CONVERSATION_DENIED", "conversation permission denied");
    }
}
