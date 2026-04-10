package com.cheeseocean.im.common.core.business.repository;

import com.cheeseocean.im.common.api.business.domain.GroupMember;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 群成员仓储抽象接口。
 *
 * <p>负责群成员关系、角色、搜索和成员列表读取。
 */
public interface GroupMemberRepository {

    /**
     * 查询群内指定成员。
     */
    Optional<GroupMember> findByGroupAndUser(String groupId, String userId);

    /**
     * 查询群主成员记录。
     */
    Optional<GroupMember> findOwner(String groupId);

    /**
     * 查询群内全部成员。
     */
    List<GroupMember> findByGroupId(String groupId);

    /**
     * 查询群内一组指定成员。
     */
    List<GroupMember> find(String groupId, List<String> userIds);

    /**
     * 查询某用户在一组群中的成员记录。
     */
    List<GroupMember> findInGroups(String userId, List<String> groupIds);

    /**
     * 查询某用户加入的群组 ID 列表。
     */
    List<String> findGroupIdsByUserId(String userId);

    /**
     * 查询某用户管理的群组 ID 列表。
     */
    List<String> findManagedGroupIds(String userId);

    /**
     * 查询群内某个角色级别的成员。
     */
    List<GroupMember> findByGroupIdAndRole(String groupId, int roleLevel);

    /**
     * 查询群内某个角色级别的成员 userId。
     */
    List<String> findRoleUserIds(String groupId, int roleLevel);

    /**
     * 查询群内全部成员 userId。
     */
    List<String> findMemberUserIds(String groupId);

    /**
     * 按群昵称搜索群成员。
     */
    List<GroupMember> searchMembers(String keyword, String groupId, int limit, int offset);

    /**
     * 判断用户是否仍在群中。
     */
    boolean existsByGroupAndUser(String groupId, String userId);

    /**
     * 统计群成员数量。
     */
    long countByGroupId(String groupId);

    /**
     * 批量保存成员关系。
     */
    void saveAll(List<GroupMember> members);

    /**
     * 按字段更新单个成员记录。
     */
    void updateFields(String groupId, String userId, Map<String, Object> fields);

    /**
     * 更新成员角色等级。
     */
    void updateRoleLevel(String groupId, String userId, int roleLevel);

    /**
     * 删除单个成员。
     */
    void remove(String groupId, String userId);

    /**
     * 批量删除成员。
     */
    void removeAll(String groupId, List<String> userIds);
}
