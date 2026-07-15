package com.cheeseocean.im.business.service.group;

import com.cheeseocean.im.common.api.enums.GroupTypeEnum;
import com.cheeseocean.im.common.api.group.GroupMembershipQueryService;
import com.cheeseocean.im.common.core.business.repository.GroupMemberRepository;
import com.cheeseocean.im.common.core.business.repository.GroupRepository;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@DubboService
public class GroupMembershipQueryServiceImpl implements GroupMembershipQueryService {

    private final GroupMemberRepository groupMemberRepository;
    private final GroupRepository groupRepository;

    public GroupMembershipQueryServiceImpl(GroupMemberRepository groupMemberRepository,
                                          GroupRepository groupRepository) {
        this.groupMemberRepository = groupMemberRepository;
        this.groupRepository = groupRepository;
    }

    @Override
    public List<String> queryConversationMembers(String conversationId) {
        if (conversationId == null) {
            return new ArrayList<>();
        }
        String groupId = stripGroupPrefix(conversationId);
        if (groupId == null) {
            return new ArrayList<>();
        }
        return groupMemberRepository.findByGroupId(groupId)
                .stream()
                .map(member -> member.getUserId())
                .toList();
    }

    @Override
    public List<String> queryGroupMembers(String groupId) {
        if (groupId == null || groupId.isBlank()) {
            return new ArrayList<>();
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

    @Override
    public Optional<com.cheeseocean.im.common.api.business.domain.Group> queryGroup(String groupId) {
        if (groupId == null || groupId.isBlank()) {
            return Optional.empty();
        }
        return groupRepository.findById(groupId);
    }

    @Override
    public GroupTypeEnum queryGroupType(String groupId) {
        if (groupId == null || groupId.isBlank()) {
            return null;
        }
        Optional<com.cheeseocean.im.common.api.business.domain.Group> group = queryGroup(groupId);
        return group.map(com.cheeseocean.im.common.api.business.domain.Group::getGroupType).orElse(null);
    }

    /**
     * 解析会话 id 中的 groupId。
     *
     * <p>只接受 {@code g:{groupId}}；其它前缀返回 null（调用方按未识别处理，返回空成员列表）。
     */
    private static String stripGroupPrefix(String conversationId) {
        if (conversationId.startsWith("g:")) {
            return conversationId.substring("g:".length());
        }
        return null;
    }
}
