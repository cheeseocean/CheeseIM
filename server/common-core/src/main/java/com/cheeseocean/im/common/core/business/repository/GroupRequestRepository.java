package com.cheeseocean.im.common.core.business.repository;

import com.cheeseocean.im.common.core.business.domain.GroupRequest;

import java.util.List;
import java.util.Optional;

/**
 * 入群申请仓储抽象接口。
 *
 * <p>定义入群申请领域对象的持久化操作。
 */
public interface GroupRequestRepository {

    /** 查询指定用户对指定群的申请，不存在时返回 empty */
    Optional<GroupRequest> findByUserAndGroup(String userId, String groupId);

    /** 查询群内待处理的申请列表（按申请时间倒序） */
    List<GroupRequest> findPendingByGroupId(String groupId);

    /** 查询用户发出的待处理申请列表 */
    List<GroupRequest> findPendingByUserId(String userId);

    /** 保存（新增或覆盖）申请记录 */
    void save(GroupRequest request);

    /** 更新处理结果 */
    void updateHandleResult(String userId, String groupId, int handleResult,
                            String handleUserId, String handledMsg, long handledTime);
}
