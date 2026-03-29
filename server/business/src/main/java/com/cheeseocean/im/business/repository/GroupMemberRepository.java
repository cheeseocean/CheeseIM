package com.cheeseocean.im.business.repository;

import com.cheeseocean.im.business.domain.GroupMember;

import java.util.List;
import java.util.Optional;

/**
 * 群成员仓储抽象接口。
 *
 * <p>定义群成员领域对象的持久化操作。
 */
public interface GroupMemberRepository {

    /** 查询群内指定用户的成员信息，不存在时返回 empty */
    Optional<GroupMember> findByGroupAndUser(String groupId, String userId);

    /** 查询群内全部成员列表 */
    List<GroupMember> findByGroupId(String groupId);

    /** 查询用户加入的所有群组 ID */
    List<String> findGroupIdsByUserId(String userId);

    /** 查询群内指定角色的成员列表（如查询所有管理员） */
    List<GroupMember> findByGroupIdAndRole(String groupId, int roleLevel);

    /** 检查用户是否在群内 */
    boolean existsByGroupAndUser(String groupId, String userId);

    /** 统计群成员数量 */
    long countByGroupId(String groupId);

    /** 保存成员（新增或覆盖） */
    void save(GroupMember member);

    /** 批量保存 */
    void saveAll(List<GroupMember> members);

    /** 删除成员（踢出群） */
    void remove(String groupId, String userId);

    /** 批量删除成员 */
    void removeAll(String groupId, List<String> userIds);
}
