package com.cheeseocean.im.common.core.business.repository;

import com.cheeseocean.im.common.api.business.domain.User;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 用户资料仓储抽象接口。
 *
 * <p>负责用户基础资料、通知账号筛选以及全局接收设置读取。
 */
public interface UserRepository {

    /**
     * 根据用户 ID 查询单个用户。
     */
    Optional<User> findById(String userId);

    /**
     * 批量查询用户资料。
     */
    List<User> findByIds(List<String> userIds);

    /**
     * 批量保存或覆盖用户资料。
     */
    void saveAll(List<User> users);

    /**
     * 按字段局部更新用户资料。
     */
    void updateFields(String userId, Map<String, Object> fields);

    /**
     * 判断用户是否存在。
     */
    boolean exists(String userId);

    /**
     * 按昵称模糊匹配查询用户。
     */
    List<User> findByNickname(String nickname);

    /**
     * 查询管理员级别大于等于指定值的用户。
     */
    List<User> findByAppManagerLevelGte(int level);

    /**
     * 分页查询普通用户列表。
     */
    List<User> pageAll(int limit, int offset);

    /**
     * 统计普通用户总数。
     */
    long countAll();

    /**
     * 按关键字分页查询普通用户。
     */
    List<User> pageByKeyword(String keyword, int limit, int offset);

    /**
     * 按关键字统计普通用户总数。
     */
    long countByKeyword(String keyword);

    /**
     * 分页读取全量用户 ID。
     */
    List<String> findAllUserIds(int limit, int offset);

    /**
     * 从候选用户 ID 中筛出实际存在的用户。
     */
    List<String> findExistingUserIds(List<String> userIds);

    /**
     * 分页查询通知/系统账号。
     */
    List<User> pageNotificationAccounts(String keyword, Integer appManagerLevel, int limit, int offset);

    /**
     * 读取用户全局消息接收设置。
     */
    int getGlobalReceiveOption(String userId);
}
