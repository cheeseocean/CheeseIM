package com.cheeseocean.im.business.service.group;

import com.cheeseocean.im.common.api.business.domain.Group;
import com.cheeseocean.im.common.api.business.domain.GroupMember;
import com.cheeseocean.im.common.api.business.domain.GroupMemberEpoch;
import com.cheeseocean.im.common.api.group.GroupMembershipChangeResult;
import com.cheeseocean.im.common.api.group.GroupMembershipCommandService;
import com.cheeseocean.im.common.core.business.repository.GroupMemberEpochRepository;
import com.cheeseocean.im.common.core.business.repository.GroupMemberRepository;
import com.cheeseocean.im.common.core.business.repository.GroupRepository;
import com.cheeseocean.im.common.core.business.transaction.PersistenceTransactionExecutor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 群成员关系统一写入口。
 *
 * <p>生产 cluster profile 在一个 Mongo 事务内更新当前态、历史 epoch 和群版本。
 * all-in-one 为降低本地环境门槛仍可无事务运行，但不得作为生产部署方式。</p>
 */
@Service
@DubboService
public class GroupMembershipCommandServiceImpl implements GroupMembershipCommandService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupMemberEpochRepository groupMemberEpochRepository;
    private final GroupMembershipSnapshotInitializer snapshotInitializer;
    private final PersistenceTransactionExecutor transactionExecutor;
    private final GroupPermissionMetadataCache permissionMetadataCache;

    public GroupMembershipCommandServiceImpl(
            GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            GroupMemberEpochRepository groupMemberEpochRepository,
            GroupMembershipSnapshotInitializer snapshotInitializer,
            PersistenceTransactionExecutor transactionExecutor,
            GroupPermissionMetadataCache permissionMetadataCache) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.groupMemberEpochRepository = groupMemberEpochRepository;
        this.snapshotInitializer = snapshotInitializer;
        this.transactionExecutor = transactionExecutor;
        this.permissionMetadataCache = permissionMetadataCache;
    }

    @Override
    public GroupMembershipChangeResult addMembers(String groupId, List<GroupMember> members) {
        requireGroupId(groupId);
        Group group = requireInitializedGroup(groupId);
        Map<String, GroupMember> requested = normalizeMembers(groupId, members);
        if (requested.isEmpty()) {
            return result(groupId, group.getMembershipVersion(), List.of());
        }
        List<GroupMember> candidates = requested.values().stream().toList();
        AtomicLong version = new AtomicLong();
        AtomicReference<List<GroupMember>> changed = new AtomicReference<>(List.of());
        transactionExecutor.execute(() -> {
            Map<String, GroupMember> pending = new LinkedHashMap<>();
            candidates.forEach(member -> pending.put(member.getUserId(), member));
            groupMemberRepository.find(groupId, pending.keySet().stream().toList())
                    .forEach(member -> pending.remove(member.getUserId()));
            List<GroupMember> additions = pending.values().stream().toList();
            if (additions.isEmpty()) {
                version.set(currentVersion(groupId));
                return;
            }
            long nextVersion = requireNextVersion(groupId);
            List<GroupMemberEpoch> epochs = additions.stream()
                    .map(member -> openEpoch(member, nextVersion))
                    .toList();
            groupMemberEpochRepository.openAll(epochs);
            groupMemberRepository.saveAll(additions);
            changed.set(additions);
            version.set(nextVersion);
        });
        GroupMembershipChangeResult result = result(
                groupId,
                version.get(),
                changed.get().stream().map(GroupMember::getUserId).toList());
        if (!result.getChangedUserIds().isEmpty()) {
            permissionMetadataCache.evict(groupId);
        }
        return result;
    }

    @Override
    public GroupMembershipChangeResult removeMembers(String groupId, List<String> userIds) {
        requireGroupId(groupId);
        Group group = requireInitializedGroup(groupId);
        List<String> normalized = normalizeUserIds(userIds);
        if (normalized.isEmpty()) {
            return result(groupId, group.getMembershipVersion(), List.of());
        }
        AtomicLong version = new AtomicLong();
        AtomicReference<List<String>> changed = new AtomicReference<>(List.of());
        transactionExecutor.execute(() -> {
            List<String> removals = groupMemberRepository.find(groupId, normalized)
                    .stream()
                    .map(GroupMember::getUserId)
                    .toList();
            if (removals.isEmpty()) {
                version.set(currentVersion(groupId));
                return;
            }
            long nextVersion = requireNextVersion(groupId);
            groupMemberEpochRepository.closeAll(groupId, removals, nextVersion);
            groupMemberRepository.removeAll(groupId, removals);
            changed.set(removals);
            version.set(nextVersion);
        });
        GroupMembershipChangeResult result = result(groupId, version.get(), changed.get());
        if (!result.getChangedUserIds().isEmpty()) {
            permissionMetadataCache.evict(groupId);
        }
        return result;
    }

    private Group requireInitializedGroup(String groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group does not exist: " + groupId));
        long version = snapshotInitializer.ensureInitialized(group);
        group.setMembershipVersion(version);
        return group;
    }

    private long currentVersion(String groupId) {
        return groupRepository.findById(groupId).map(Group::getMembershipVersion).orElse(0L);
    }

    private long requireNextVersion(String groupId) {
        long version = groupRepository.incrementMembershipVersion(groupId);
        if (version <= 0L) {
            throw new IllegalStateException("Failed to allocate group membership version: " + groupId);
        }
        return version;
    }

    private Map<String, GroupMember> normalizeMembers(String groupId, List<GroupMember> members) {
        Map<String, GroupMember> normalized = new LinkedHashMap<>();
        if (members == null) {
            return normalized;
        }
        for (GroupMember member : members) {
            if (member == null || member.getUserId() == null || member.getUserId().isBlank()) {
                continue;
            }
            member.setGroupId(groupId);
            normalized.putIfAbsent(member.getUserId(), member);
        }
        return normalized;
    }

    private List<String> normalizeUserIds(List<String> userIds) {
        if (userIds == null) {
            return List.of();
        }
        return userIds.stream()
                .filter(userId -> userId != null && !userId.isBlank())
                .distinct()
                .toList();
    }

    private GroupMemberEpoch openEpoch(GroupMember member, long version) {
        GroupMemberEpoch epoch = new GroupMemberEpoch();
        epoch.setEpochId(UUID.nameUUIDFromBytes(
                ("group-member-epoch:" + member.getGroupId() + ":" + member.getUserId() + ":" + version)
                        .getBytes(StandardCharsets.UTF_8)).toString());
        epoch.setGroupId(member.getGroupId());
        epoch.setUserId(member.getUserId());
        epoch.setJoinedVersion(version);
        epoch.setLeftVersionExclusive(Long.MAX_VALUE);
        return epoch;
    }

    private GroupMembershipChangeResult result(String groupId,
                                               long membershipVersion,
                                               List<String> changedUserIds) {
        GroupMembershipChangeResult result = new GroupMembershipChangeResult();
        result.setGroupId(groupId);
        result.setMembershipVersion(membershipVersion);
        result.setChangedUserIds(changedUserIds);
        return result;
    }

    private void requireGroupId(String groupId) {
        if (groupId == null || groupId.isBlank()) {
            throw new IllegalArgumentException("groupId is required");
        }
    }
}
