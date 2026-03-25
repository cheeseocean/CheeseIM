package com.cheeseocean.im.postmaster.service;

import com.cheeseocean.im.common.api.group.GroupMembershipQueryDubboService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GroupMembershipFacade {

    @DubboReference(check = false)
    private GroupMembershipQueryDubboService groupMembershipQueryDubboService;

    public GroupMembershipFacade() {
    }

    GroupMembershipFacade(GroupMembershipQueryDubboService groupMembershipQueryDubboService) {
        this.groupMembershipQueryDubboService = groupMembershipQueryDubboService;
    }

    public List<String> loadTargets(String conversationId) {
        if (groupMembershipQueryDubboService == null) {
            throw new IllegalStateException("GroupMembershipQueryDubboService is not configured");
        }
        return groupMembershipQueryDubboService.queryMembers(conversationId);
    }
}
