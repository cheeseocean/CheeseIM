package com.cheeseocean.im.postmaster.service;

import com.cheeseocean.im.common.api.enums.GroupTypeEnum;
import com.cheeseocean.im.common.api.group.GroupMembershipQueryService;
import com.cheeseocean.im.common.api.group.GroupMemberPage;
import com.cheeseocean.im.common.api.permission.GroupMessageSendPermissionRequest;
import com.cheeseocean.im.common.api.permission.GroupMessageSendPermissionResult;
import com.cheeseocean.im.common.api.permission.GroupMessageSendPermissionService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GroupMembershipFacade {

    @DubboReference(check = false, retries = 0)
    private GroupMembershipQueryService groupMembershipQueryService;

    @DubboReference(check = false, retries = 0)
    private GroupMessageSendPermissionService groupMessageSendPermissionService;

    public GroupMembershipFacade() {
    }

    GroupMembershipFacade(GroupMembershipQueryService groupMembershipQueryService) {
        this.groupMembershipQueryService = groupMembershipQueryService;
    }

    /**
     * 批量校验同一群内的消息发送者，并同时返回群扩散类型。
     */
    public GroupMessageSendPermissionResult checkSendPermissions(String groupId,
                                                                 List<String> senderIds) {
        if (groupMessageSendPermissionService == null) {
            throw new IllegalStateException("GroupMessageSendPermissionService is not configured");
        }
        GroupMessageSendPermissionRequest request = new GroupMessageSendPermissionRequest();
        request.setGroupId(groupId);
        request.setSenderIds(senderIds);
        return groupMessageSendPermissionService.check(request);
    }

    /**
     * 查询群成员 userId 列表。
     *
     * <p>历史上有 {@code loadDeliveryTargets(String conversationId)} 同义方法，但已无调用方；
     * 群写扩散改造（P0-2）后 postmaster 直接按 groupId 查询并按成员切片 publish，不再经会话维度。
     */
    public List<String> loadGroupMembers(String groupId) {
        if (groupMembershipQueryService == null) {
            throw new IllegalStateException("GroupMembershipQueryDubboService is not configured");
        }
        return groupMembershipQueryService.queryGroupMembers(groupId);
    }

    /** 供 fanout worker 使用的稳定 keyset 分页入口。 */
    public GroupMemberPage loadGroupMembersPage(String groupId,
                                                long snapshotVersion,
                                                long afterJoinedVersion,
                                                String afterUserId,
                                                String afterEpochId,
                                                int limit) {
        if (groupMembershipQueryService == null) {
            throw new IllegalStateException("GroupMembershipQueryDubboService is not configured");
        }
        return groupMembershipQueryService.queryGroupMembersPage(
                groupId, snapshotVersion, afterJoinedVersion, afterUserId, afterEpochId, limit);
    }

    /** 判断用户是否仍为群成员，供历史与 mutation 读路径做权限校验。 */
    public boolean isGroupMember(String groupId, String userId) {
        if (groupMembershipQueryService == null) {
            throw new IllegalStateException("GroupMembershipQueryDubboService is not configured");
        }
        return groupMembershipQueryService.isGroupMember(groupId, userId);
    }

    /**
     * 查询群类型，决定群消息扩散模式：
     * <ul>
     *   <li>{@link GroupTypeEnum#NORMAL_GROUP}：写扩散（postmaster 按成员 publish N 个 keyed DeliveryEvent）</li>
     *   <li>{@link GroupTypeEnum#SUPER_GROUP}：读扩散（postmaster 仅持久化，客户端按 seq 拉取）</li>
     *   <li>{@code null}：群不存在或查询异常，调用方按"默认写扩散"或"跳过"自降级</li>
     * </ul>
     */
    public GroupTypeEnum loadGroupType(String groupId) {
        if (groupMembershipQueryService == null) {
            throw new IllegalStateException("GroupMembershipQueryDubboService is not configured");
        }
        return groupMembershipQueryService.queryGroupType(groupId);
    }
}
