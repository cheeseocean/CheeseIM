package com.cheeseocean.im.postbox.policy;

import com.cheeseocean.im.postbox.service.GroupMemberService;
import org.springframework.stereotype.Component;

@Component
public class GroupChatPolicy {

    private final GroupMemberService groupMemberService;

    public GroupChatPolicy(GroupMemberService groupMemberService) {
        this.groupMemberService = groupMemberService;
    }

    public boolean canAccess(String conversationId, String userId) {
        if (conversationId == null || userId == null || !conversationId.startsWith("group:")) {
            return false;
        }
        return groupMemberService.isGroupMember(conversationId.substring("group:".length()), userId);
    }
}
