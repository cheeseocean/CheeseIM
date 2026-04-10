package com.cheeseocean.im.common.core.business.repository;

import com.cheeseocean.im.common.api.business.domain.Group;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 群组仓储抽象接口。
 *
 * <p>负责群资料、群状态以及群搜索列表的持久化读取。
 */
public interface GroupRepository {

    /**
     * 查询单个群组。
     */
    Optional<Group> findById(String groupId);

    /**
     * 批量查询群组。
     */
    List<Group> findByIds(List<String> groupIds);

    /**
     * 批量保存群组资料。
     */
    void saveAll(List<Group> groups);

    /**
     * 按字段更新群组资料。
     */
    void updateFields(String groupId, Map<String, Object> fields);

    /**
     * 判断群组是否存在。
     */
    boolean exists(String groupId);

    /**
     * 更新群状态。
     */
    void updateStatus(String groupId, int status);

    /**
     * 按群名称关键字分页搜索群组。
     */
    List<Group> pageByKeyword(String keyword, int limit, int offset);

    /**
     * 统计符合关键字的群组总数。
     */
    long countByKeyword(String keyword);

    /**
     * 按稳定排序规则返回一批已加入群组的 ID。
     */
    List<String> findJoinSortedGroupIds(List<String> groupIds);

    /**
     * 对已加入群组集合按关键字分页查询。
     */
    List<Group> pageJoinedGroups(List<String> groupIds, String keyword, int limit, int offset);
}
