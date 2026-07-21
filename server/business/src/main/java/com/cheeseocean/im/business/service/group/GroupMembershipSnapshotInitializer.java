package com.cheeseocean.im.business.service.group;

import com.cheeseocean.im.common.api.business.domain.Group;
import com.cheeseocean.im.common.api.business.domain.GroupMember;
import com.cheeseocean.im.common.api.business.domain.GroupMemberEpoch;
import com.cheeseocean.im.common.core.business.repository.GroupMemberEpochRepository;
import com.cheeseocean.im.common.core.business.repository.GroupMemberRepository;
import com.cheeseocean.im.common.core.business.repository.GroupRepository;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * 为存量群建立成员 epoch 基线。
 *
 * <p>先幂等写入确定性 epoch，再 CAS 发布版本 1；即使 all-in-one 未启用 Mongo 事务，
 * 读路径也不会在 epoch 尚未就绪时观察到可用版本。后续成员变更必须走版本化 mutation 入口。</p>
 */
@Component
public class GroupMembershipSnapshotInitializer {

    private static final long BASELINE_VERSION = 1L;
    private static final long ACTIVE_EPOCH_END = Long.MAX_VALUE;

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupMemberEpochRepository groupMemberEpochRepository;

    public GroupMembershipSnapshotInitializer(GroupRepository groupRepository,
                                              GroupMemberRepository groupMemberRepository,
                                              GroupMemberEpochRepository groupMemberEpochRepository) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.groupMemberEpochRepository = groupMemberEpochRepository;
    }

    /**
     * 返回可供权限结果和扩散任务使用的权威成员版本。
     */
    public long ensureInitialized(Group group) {
        if (group == null || group.getGroupId() == null || group.getGroupId().isBlank()) {
            throw new IllegalArgumentException("Group is required for membership baseline");
        }
        if (group.getMembershipVersion() > 0L) {
            return group.getMembershipVersion();
        }
        List<GroupMemberEpoch> baseline = groupMemberRepository.findByGroupId(group.getGroupId())
                .stream()
                .map(this::baselineEpoch)
                .toList();
        groupMemberEpochRepository.saveBaseline(baseline);
        groupRepository.initializeMembershipVersion(group.getGroupId());
        long publishedVersion = groupRepository.findById(group.getGroupId())
                .map(Group::getMembershipVersion)
                .orElse(0L);
        if (publishedVersion <= 0L) {
            throw new IllegalStateException(
                    "Failed to publish group membership baseline: groupId=" + group.getGroupId());
        }
        return publishedVersion;
    }

    private GroupMemberEpoch baselineEpoch(GroupMember member) {
        GroupMemberEpoch epoch = new GroupMemberEpoch();
        epoch.setEpochId(UUID.nameUUIDFromBytes(
                ("group-member-baseline:" + member.getGroupId() + ":" + member.getUserId())
                        .getBytes(StandardCharsets.UTF_8)).toString());
        epoch.setGroupId(member.getGroupId());
        epoch.setUserId(member.getUserId());
        epoch.setJoinedVersion(BASELINE_VERSION);
        epoch.setLeftVersionExclusive(ACTIVE_EPOCH_END);
        return epoch;
    }
}
