package com.cheeseocean.im.common.api.group;

import com.cheeseocean.im.common.api.business.domain.GroupMember;

import java.util.List;

/**
 * 群成员关系写契约。
 *
 * <p>所有入群、退群、踢人和批量导入最终都必须收口到此契约，禁止业务层直接组合多个仓储写操作。</p>
 */
public interface GroupMembershipCommandService {

    /**
     * 批量添加尚未在群内的成员；同一批共享一个成员版本。
     */
    GroupMembershipChangeResult addMembers(String groupId, List<GroupMember> members);

    /**
     * 批量移除当前群成员；同一批共享一个成员版本。
     */
    GroupMembershipChangeResult removeMembers(String groupId, List<String> userIds);
}
