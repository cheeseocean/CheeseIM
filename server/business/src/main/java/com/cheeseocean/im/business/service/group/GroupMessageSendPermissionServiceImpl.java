package com.cheeseocean.im.business.service.group;

import com.cheeseocean.im.common.api.business.domain.Group;
import com.cheeseocean.im.common.api.business.domain.GroupMember;
import com.cheeseocean.im.common.api.enums.GroupSendPermissionCode;
import com.cheeseocean.im.common.api.enums.GroupStatusEnum;
import com.cheeseocean.im.common.api.permission.GroupMessageSendPermissionDecision;
import com.cheeseocean.im.common.api.permission.GroupMessageSendPermissionRequest;
import com.cheeseocean.im.common.api.permission.GroupMessageSendPermissionResult;
import com.cheeseocean.im.common.api.permission.GroupMessageSendPermissionService;
import com.cheeseocean.im.common.core.business.repository.GroupMemberRepository;
import com.cheeseocean.im.common.core.business.repository.GroupRepository;
import com.cheeseocean.im.common.core.cache.CacheRegion;
import com.cheeseocean.im.common.core.cache.CacheStore;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 群消息发送权限聚合实现。
 *
 * <p>每批只点查一次群资料，并通过 compound index 批量读取发送者成员记录；
 * 不加载全量群成员，超级群发送校验也保持 O(发送者数)。</p>
 */
@Service
@DubboService
public class GroupMessageSendPermissionServiceImpl implements GroupMessageSendPermissionService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupMembershipSnapshotInitializer snapshotInitializer;
    private final GroupPermissionMetadataCache metadataCache;
    private final CacheRegion<GroupSenderPermissionSnapshot> permissionCache;

    public GroupMessageSendPermissionServiceImpl(GroupRepository groupRepository,
                                                 GroupMemberRepository groupMemberRepository,
                                                 GroupMembershipSnapshotInitializer snapshotInitializer,
                                                 GroupPermissionMetadataCache metadataCache,
                                                 CacheStore cacheStore) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.snapshotInitializer = snapshotInitializer;
        this.metadataCache = metadataCache;
        this.permissionCache = cacheStore.region(
                "group:send-permission:v2:",
                GroupSenderPermissionSnapshot.class,
                Duration.ofSeconds(2));
    }

    @Override
    public GroupMessageSendPermissionResult check(GroupMessageSendPermissionRequest request) {
        String groupId = request == null ? null : request.getGroupId();
        List<String> senderIds = normalizeSenderIds(request == null ? null : request.getSenderIds());
        GroupMessageSendPermissionResult result = new GroupMessageSendPermissionResult();
        result.setGroupId(groupId);
        if (groupId == null || groupId.isBlank() || senderIds.isEmpty()) {
            result.setDecisions(decisions(senderIds, GroupSendPermissionCode.INVALID_REQUEST, 0L));
            return result;
        }
        GroupPermissionMetadataSnapshot metadata = metadataCache.getOrLoad(
                groupId, () -> loadMetadata(groupId));

        Map<String, String> cacheKeys = new LinkedHashMap<>();
        senderIds.forEach(senderId -> cacheKeys.put(
                senderId, permissionCacheKey(groupId, metadata.getMembershipVersion(), senderId)));
        Map<String, GroupSenderPermissionSnapshot> cachedByKey =
                permissionCache.getAll(cacheKeys.values());
        Map<String, GroupSenderPermissionSnapshot> snapshots = new LinkedHashMap<>();
        cacheKeys.forEach((senderId, key) -> {
            GroupSenderPermissionSnapshot snapshot = cachedByKey.get(key);
            if (snapshot != null) {
                snapshots.put(senderId, snapshot);
            }
        });
        List<String> missingSenderIds = senderIds.stream()
                .filter(senderId -> !snapshots.containsKey(senderId))
                .toList();
        if (!missingSenderIds.isEmpty()) {
            Map<String, GroupSenderPermissionSnapshot> loaded =
                    loadSnapshots(groupId, missingSenderIds, metadata);
            snapshots.putAll(loaded);
            Map<String, GroupSenderPermissionSnapshot> cacheValues = new LinkedHashMap<>();
            loaded.forEach((senderId, snapshot) ->
                    cacheValues.put(cacheKeys.get(senderId), snapshot));
            permissionCache.putAll(cacheValues);
        }

        GroupSenderPermissionSnapshot sample = snapshots.values().stream().findFirst().orElse(null);
        result.setGroupType(sample == null ? null : sample.getGroupType());
        result.setMembershipVersion(sample == null ? 0L : sample.getMembershipVersion());
        result.setDecisions(senderIds.stream()
                .map(senderId -> toDecision(senderId, snapshots.get(senderId)))
                .toList());
        return result;
    }

    private GroupPermissionMetadataSnapshot loadMetadata(String groupId) {
        GroupPermissionMetadataSnapshot metadata = new GroupPermissionMetadataSnapshot();
        Group group = groupRepository.findById(groupId).orElse(null);
        if (group == null) {
            return metadata;
        }
        metadata.setExists(true);
        metadata.setGroupType(group.getGroupType());
        metadata.setStatusCode(group.getStatus().getCode());
        metadata.setMembershipVersion(snapshotInitializer.ensureInitialized(group));
        return metadata;
    }

    private Map<String, GroupSenderPermissionSnapshot> loadSnapshots(
            String groupId,
            List<String> senderIds,
            GroupPermissionMetadataSnapshot metadata) {
        if (!metadata.isExists()) {
            return snapshots(senderIds, null, 0L, GroupSendPermissionCode.GROUP_NOT_FOUND, 0L);
        }
        long membershipVersion = metadata.getMembershipVersion();
        GroupSendPermissionCode groupDenial = groupDenial(
                GroupStatusEnum.fromCode(metadata.getStatusCode()));
        if (groupDenial != null) {
            return snapshots(senderIds, metadata.getGroupType(), membershipVersion, groupDenial, 0L);
        }
        Map<String, GroupMember> members = new LinkedHashMap<>();
        for (GroupMember member : groupMemberRepository.find(groupId, senderIds)) {
            if (member != null && member.getUserId() != null) {
                members.put(member.getUserId(), member);
            }
        }
        long now = System.currentTimeMillis();
        Map<String, GroupSenderPermissionSnapshot> result = new LinkedHashMap<>();
        for (String senderId : senderIds) {
            GroupMember member = members.get(senderId);
            if (member == null) {
                result.put(senderId, snapshot(
                        metadata.getGroupType(), membershipVersion,
                        GroupSendPermissionCode.NOT_MEMBER, 0L));
            } else if (member.getMuteEndTime() > now) {
                result.put(senderId, snapshot(
                        metadata.getGroupType(),
                        membershipVersion,
                        GroupSendPermissionCode.MEMBER_MUTED,
                        member.getMuteEndTime()));
            } else {
                result.put(senderId, snapshot(
                        metadata.getGroupType(), membershipVersion,
                        GroupSendPermissionCode.ALLOWED, 0L));
            }
        }
        return result;
    }

    private static GroupMessageSendPermissionDecision toDecision(
            String senderId,
            GroupSenderPermissionSnapshot snapshot) {
        if (snapshot == null) {
            return GroupMessageSendPermissionDecision.of(
                    senderId, GroupSendPermissionCode.INVALID_REQUEST, 0L);
        }
        return GroupMessageSendPermissionDecision.of(
                senderId,
                GroupSendPermissionCode.fromCode(snapshot.getPermissionCode()),
                snapshot.getMuteEndTime());
    }

    private static List<String> normalizeSenderIds(List<String> senderIds) {
        if (senderIds == null) {
            return List.of();
        }
        return senderIds.stream()
                .filter(senderId -> senderId != null && !senderId.isBlank())
                .distinct()
                .toList();
    }

    private static GroupSendPermissionCode groupDenial(GroupStatusEnum status) {
        if (status == GroupStatusEnum.NORMAL) {
            return null;
        }
        if (status == GroupStatusEnum.DISBANDED) {
            return GroupSendPermissionCode.GROUP_DISBANDED;
        }
        return GroupSendPermissionCode.GROUP_BANNED;
    }

    private static List<GroupMessageSendPermissionDecision> decisions(
            List<String> senderIds,
            GroupSendPermissionCode permission,
            long muteEndTime) {
        return senderIds.stream()
                .map(senderId -> GroupMessageSendPermissionDecision.of(
                        senderId, permission, muteEndTime))
                .toList();
    }

    private static Map<String, GroupSenderPermissionSnapshot> snapshots(
            List<String> senderIds,
            com.cheeseocean.im.common.api.enums.GroupTypeEnum groupType,
            long membershipVersion,
            GroupSendPermissionCode permission,
            long muteEndTime) {
        Map<String, GroupSenderPermissionSnapshot> result = new LinkedHashMap<>();
        senderIds.forEach(senderId -> result.put(
                senderId,
                snapshot(groupType, membershipVersion, permission, muteEndTime)));
        return result;
    }

    private static GroupSenderPermissionSnapshot snapshot(
            com.cheeseocean.im.common.api.enums.GroupTypeEnum groupType,
            long membershipVersion,
            GroupSendPermissionCode permission,
            long muteEndTime) {
        GroupSenderPermissionSnapshot snapshot = new GroupSenderPermissionSnapshot();
        snapshot.setGroupType(groupType);
        snapshot.setMembershipVersion(membershipVersion);
        snapshot.setPermissionCode(permission.getCode());
        snapshot.setMuteEndTime(muteEndTime);
        return snapshot;
    }

    private static String permissionCacheKey(String groupId,
                                             long membershipVersion,
                                             String senderId) {
        // 同群发送者使用共同 hash tag，使 batch MGET/pipeline 在 Redis Cluster 中保持同槽。
        return "{group-send:" + sha256(groupId) + "}:"
                + membershipVersion + ":" + sha256(senderId);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
