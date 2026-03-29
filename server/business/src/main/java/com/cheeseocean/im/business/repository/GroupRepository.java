package com.cheeseocean.im.business.repository;

import com.cheeseocean.im.business.domain.Group;

import java.util.List;
import java.util.Optional;

/**
 * 群组仓储抽象接口。
 *
 * <p>定义群组领域对象的基础持久化操作。
 */
public interface GroupRepository {

    /** 根据群组 ID 查询，不存在时返回 empty */
    Optional<Group> findById(String groupId);

    /** 批量查询，不存在的跳过 */
    List<Group> findByIds(List<String> groupIds);

    /** 保存（新增或覆盖）群组 */
    void save(Group group);

    /** 局部字段更新 */
    void updateFields(String groupId, java.util.Map<String, Object> fields);

    /** 检查群组是否存在 */
    boolean existsById(String groupId);

    /** 删除群组（逻辑删除：将状态置为 DISBANDED） */
    void disband(String groupId);
}
