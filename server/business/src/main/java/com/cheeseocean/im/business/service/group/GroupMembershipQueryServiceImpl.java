package com.cheeseocean.im.business.service.group;

import com.cheeseocean.im.common.api.group.GroupMembershipQueryService;
import com.cheeseocean.im.common.core.business.repository.GroupMemberRepository;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@DubboService
public class GroupMembershipQueryServiceImpl implements GroupMembershipQueryService {

    private final GroupMemberRepository groupMemberRepository;

    public GroupMembershipQueryServiceImpl(GroupMemberRepository groupMemberRepository) {
        this.groupMemberRepository = groupMemberRepository;
    }

    @Override
    public List<String> queryConversationMembers(String conversationId) {
        if (conversationId == null || !conversationId.startsWith("c2:")) {
            return List.of();
        }
        return groupMemberRepository.findByGroupId(conversationId.substring("c2:".length()))
                .stream()
                .map(member -> member.getUserId())
                .toList();
    }

    @Override
    public List<String> queryGroupMembers(String groupId) {
        if (groupId == null || groupId.isBlank()) {
            return List.of();
        }
        return groupMemberRepository.findByGroupId(groupId)
                .stream()
                .map(member -> member.getUserId())
                .toList();
    }

    @Override
    public boolean isGroupMember(String groupId, String userId) {
        return groupId != null
                && userId != null
                && groupMemberRepository.existsByGroupAndUser(groupId, userId);
    }
}
