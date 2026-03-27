package com.cheeseocean.im.postmaster.service;

import com.cheeseocean.im.common.api.group.GroupMembershipQueryService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GroupMembershipFacade {

    @DubboReference(check = false)
    private GroupMembershipQueryService groupMembershipQueryService;

    public GroupMembershipFacade() {
    }

    GroupMembershipFacade(GroupMembershipQueryService groupMembershipQueryService) {
        this.groupMembershipQueryService = groupMembershipQueryService;
    }

    public List<String> loadTargets(String conversationId) {
        if (groupMembershipQueryService == null) {
            throw new IllegalStateException("GroupMembershipQueryDubboService is not configured");
        }
        return groupMembershipQueryService.queryMembers(conversationId);
    }
}
