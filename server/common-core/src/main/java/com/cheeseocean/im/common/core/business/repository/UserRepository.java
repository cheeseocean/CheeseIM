package com.cheeseocean.im.common.core.business.repository;

import com.cheeseocean.im.common.core.business.domain.User;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 用户仓储抽象接口。
 *
 * <p>定义用户领域对象的持久化协议，与底层存储（MongoDB / MySQL / PG）解耦。
 * 所有方法只接受和返回领域对象，不暴露任何持久化框架类型。
 */
public interface UserRepository {

    /** 根据用户 ID 查询，不存在时返回 empty */
    Optional<User> findById(String userId);

    /** 批量查询，保留入参顺序，不存在的跳过 */
    List<User> findByIds(List<String> userIds);

    /** 分页查询普通用户（appManagerLevel < 2），支持 keyword 模糊搜索昵称或精确匹配 userId */
    List<User> queryUsers(String keyword, int pageNum, int pageSize);

    /** 统计普通用户总数，条件与 {@link #queryUsers} 一致 */
    long countUsers(String keyword);

    /** 分页获取全量用户 ID（按注册时间升序） */
    List<String> findAllUserIds(int pageNum, int pageSize);

    /** 从候选列表中过滤出实际存在的用户 ID */
    List<String> filterExistingIds(List<String> userIds);

    /** 保存（新增或全量覆盖）单个用户 */
    void save(User user);

    /** 批量保存 */
    void saveAll(List<User> users);

    /**
     * 局部字段更新（只更新 fields 中包含的字段，null 字段不参与）。
     *
     * @param fields key 为 BSON 字段名，value 为新值
     */
    void updateFields(String userId, Map<String, Object> fields);

    /** 检查用户 ID 是否已存在 */
    boolean existsById(String userId);

    /** 查询通知/系统账号（appManagerLevel >= 2） */
    List<User> queryNotificationAccounts(String keyword, Integer appManagerLevel, int pageNum, int pageSize);
}
