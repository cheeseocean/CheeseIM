package com.cheeseocean.im.postmaster.service;

import com.cheeseocean.im.common.api.enums.GroupTypeEnum;
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

    public List<String> loadDeliveryTargets(String conversationId) {
        if (groupMembershipQueryService == null) {
            throw new IllegalStateException("GroupMembershipQueryDubboService is not configured");
        }
        return groupMembershipQueryService.queryConversationMembers(conversationId);
    }

    public List<String> loadGroupMembers(String groupId) {
        if (groupMembershipQueryService == null) {
            throw new IllegalStateException("GroupMembershipQueryDubboService is not configured");
        }
        return groupMembershipQueryService.queryGroupMembers(groupId);
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