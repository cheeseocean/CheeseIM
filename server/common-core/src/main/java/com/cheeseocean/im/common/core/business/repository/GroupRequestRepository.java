package com.cheeseocean.im.common.core.business.repository;

import com.cheeseocean.im.common.api.business.domain.GroupRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 入群申请仓储抽象接口。
 *
 * <p>负责群申请记录的写入、分页查询和待处理计数。
 */
public interface GroupRequestRepository {

    /**
     * 查询单个用户对单个群的申请。
     */
    Optional<GroupRequest> findByUserAndGroup(String userId, String groupId);

    /**
     * 查询某个群下指定用户集合的申请记录。
     */
    List<GroupRequest> findByGroup(String groupId, List<String> userIds);

    /**
     * 批量保存入群申请。
     */
    void saveAll(List<GroupRequest> requests);

    /**
     * 删除一条申请记录。
     */
    void delete(String userId, String groupId);

    /**
     * 按字段更新申请记录。
     */
    void updateFields(String userId, String groupId, Map<String, Object> fields);

    /**
     * 分页查询某用户提交过的入群申请。
     */
    List<GroupRequest> pageByUser(String userId, List<String> groupIds, List<Integer> handleResults, int limit, int offset);

    /**
     * 分页查询一组群收到的入群申请。
     */
    List<GroupRequest> pageByGroups(List<String> groupIds, List<Integer> handleResults, int limit, int offset);

    /**
     * 统计一组群未处理的申请数。
     */
    long countUnhandled(List<String> groupIds, long afterTs);
}
