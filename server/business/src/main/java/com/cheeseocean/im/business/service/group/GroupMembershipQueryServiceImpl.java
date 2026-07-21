package com.cheeseocean.im.business.service.group;

import com.cheeseocean.im.common.api.enums.GroupTypeEnum;
import com.cheeseocean.im.common.api.group.GroupMembershipQueryService;
import com.cheeseocean.im.common.api.group.GroupMemberPage;
import com.cheeseocean.im.common.api.business.domain.GroupMemberEpoch;
import com.cheeseocean.im.common.core.business.repository.GroupMemberEpochRepository;
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
    private final GroupMemberEpochRepository groupMemberEpochRepository;
    private final GroupRepository groupRepository;

    public GroupMembershipQueryServiceImpl(GroupMemberRepository groupMemberRepository,
                                          GroupMemberEpochRepository groupMemberEpochRepository,
                                          GroupRepository groupRepository) {
        this.groupMemberRepository = groupMemberRepository;
        this.groupMemberEpochRepository = groupMemberEpochRepository;
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
    public GroupMemberPage queryGroupMembersPage(String groupId,
                                                 long snapshotVersion,
                                                 long afterJoinedVersion,
                                                 String afterUserId,
                                                 String afterEpochId,
                                                 int limit) {
        GroupMemberPage page = new GroupMemberPage();
        if (groupId == null || groupId.isBlank()) {
            return page;
        }
        if (snapshotVersion <= 0L) {
            throw new IllegalStateException(
                    "Group membership epoch baseline is unavailable: groupId=" + groupId);
        }
        int pageSize = Math.min(2_000, Math.max(1, limit));
        List<GroupMemberEpoch> loaded = groupMemberEpochRepository.findPage(
                groupId, snapshotVersion, afterJoinedVersion, afterUserId, afterEpochId, pageSize);
        boolean hasMore = loaded.size() > pageSize;
        List<GroupMemberEpoch> visible = hasMore ? loaded.subList(0, pageSize) : loaded;
        page.setUserIds(visible.stream().map(GroupMemberEpoch::getUserId).toList());
        page.setHasMore(hasMore);
        if (!visible.isEmpty()) {
            GroupMemberEpoch last = visible.get(visible.size() - 1);
            page.setNextJoinedVersion(last.getJoinedVersion());
            page.setNextUserId(last.getUserId());
            page.setNextEpochId(last.getEpochId());
        }
        return page;
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
