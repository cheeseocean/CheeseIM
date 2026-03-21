package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.api.group.GroupMembershipQueryDubboService;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.List;

@DubboService
public class GroupMembershipQueryDubboServiceImpl implements GroupMembershipQueryDubboService {

    private final GroupMemberService groupMemberService;

    public GroupMembershipQueryDubboServiceImpl(GroupMemberService groupMemberService) {
        this.groupMemberService = groupMemberService;
    }

    @Override
    public List<String> queryMembers(String conversationId) {
        return groupMemberService.queryConversationMembers(conversationId);
    }
}
