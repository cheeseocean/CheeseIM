package com.cheeseocean.im.common.api.user;

import com.cheeseocean.im.common.api.dto.user.RegisterUserRequest;
import com.cheeseocean.im.common.api.dto.user.UpdateUserInfoRequest;
import com.cheeseocean.im.common.api.business.domain.User;

import java.util.List;

/**
 * 用户基础信息 Dubbo 服务接口。
 *
 * <p>覆盖与用户信息增删改查相关的接口，
 * 包括普通用户、管理员账号以及通知系统账号的管理。
 */
public interface UserInfoService {

    // ── 查询 ──────────────────────────────────────────────────────────────────

    /**
     * 批量查询用户基础信息。
     * 结果顺序与入参 userIds 一致，不存在的用户跳过。
     */
    List<User> getUsersInfo(List<String> userIds);

    /**
     * 查询单个用户基础信息。
     * 用户不存在时返回 null。
     */
    User getUserInfo(String userId);

    /**
     * 分页查询用户列表。
     * keyword 为空时返回全量，非空时按 userId 或 nickname 模糊匹配。
     *
     * @param pageNum  页码，从 1 开始
     * @param pageSize 每页条数
     * @param keyword  搜索关键词，null 表示不过滤
     */
    List<User> pageQueryUsers(int pageNum, int pageSize, String keyword);

    /**
     * 统计用户总数。
     * 与 pageQueryUsers 配合使用。
     */
    long countUsers(String keyword);

    /**
     * 分页获取所有用户 ID 列表（无过滤条件）。
     *
     * @param pageNum  页码，从 1 开始
     * @param pageSize 每页条数
     */
    List<String> getAllUserIds(int pageNum, int pageSize);

    /**
     * 批量检查用户 ID 是否已注册。
     *
     * @return 已注册的 userId 集合
     */
    List<String> filterExistingUserIds(List<String> userIds);

    // ── 注册与更新 ────────────────────────────────────────────────────────────

    /**
     * 批量注册用户。
     * userIds 中若存在已注册的 ID，整批请求失败。
     */
    void registerUsers(List<RegisterUserRequest> requests);

    /**
     * 可选字段更新用户信息。
     * request 中 null 字段不更新；更新后使缓存失效。
     */
    void updateUserInfo(String userId, UpdateUserInfoRequest request);

    // ── 通知系统账号管理 ─────────────────────────────────────────────────────

    /**
     * 注册通知/系统账号。
     * userId 为空时自动生成；appManagerLevel 须 >= 2（通知账号级别）。
     *
     * @return 实际注册的 userId
     */
    String addNotificationAccount(String userId, String nickname, String faceUrl, int appManagerLevel);

    /**
     * 更新通知账号信息（仅昵称和头像）。
     */
    void updateNotificationAccount(String userId, String nickname, String faceUrl);

    /**
     * 分页搜索通知账号。
     * keyword 为空时返回全部通知账号。
     *
     * @param appManagerLevel 按管理员级别过滤，null 表示不过滤
     */
    List<User> searchNotificationAccounts(String keyword, Integer appManagerLevel, int pageNum, int pageSize);

    /**
     * 查询单个通知账号信息。
     * 若 userId 对应的账号级别不足（非通知账号），返回 null。
     */
    User getNotificationAccount(String userId);

    // ── 用户设置 ──────────────────────────────────────────────────────────────

    /**
     * 返回用户的全局消息接收选项 code。
     * 未设置时返回 {@link com.cheeseocean.im.common.api.enums.ReceiveOption#RECEIVE RECEIVE}（0）。
     */
    int getReceiveOptions(String userId);

}
