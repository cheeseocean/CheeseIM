package com.cheeseocean.im.postbox.policy;

import com.cheeseocean.im.common.api.group.GroupMembershipQueryService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

@Component
public class GroupChatPolicy {

    @DubboReference
    private GroupMembershipQueryService groupMemberService;

    public boolean canAccess(String conversationId, String userId) {
        if (conversationId == null || userId == null || !conversationId.startsWith("group:")) {
            return false;
        }
        return groupMemberService.isGroupMember(conversationId.substring("group:".length()), userId);
    }
}
