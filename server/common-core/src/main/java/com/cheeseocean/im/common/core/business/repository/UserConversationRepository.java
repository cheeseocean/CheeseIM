package com.cheeseocean.im.common.core.business.repository;

import com.cheeseocean.im.common.api.business.domain.UserConversation;

import java.util.List;
import java.util.Map;

/**
 * 用户-会话业务状态仓储抽象接口。
 *
 * <p>管理 {@link UserConversation} 的持久化，
 * 仅涵盖业务配置类字段（置顶、免打扰、草稿等）以及少量兼容性展示字段。
 * 序列号同步字段（maxSeq / minSeq / readSeq）由偏移量仓储负责。
 */
public interface UserConversationRepository {

    /**
     * 若会话记录不存在则插入，已存在则忽略（幂等）。
     */
    void createIfAbsent(UserConversation conversation);

    /** 批量保存会话业务状态。 */
    void saveAll(List<UserConversation> conversations);

    /**
     * 按字段更新单条会话。
     *
     * @param fields key 为 BSON 字段名，value 为新值
     */
    void updateFields(String ownerUserId, String conversationId, Map<String, Object> fields);

    /**
     * 按字段批量更新同一会话 ID 的多个用户会话记录。
     *
     * @param ownerUserIds 目标用户 ID 列表
     * @param fields       key 为 BSON 字段名，value 为新值
     */
    void updateBatchFields(List<String> ownerUserIds, String conversationId, Map<String, Object> fields);

    /**
     * 删除指定用户维度的会话状态。
     *
     * <p>只影响 ownerUserId 自己的会话列表，不删除历史消息，也不影响其他参与者。
     */
    void delete(String ownerUserId, String conversationId);

    /** 查询单条会话业务状态，不存在时返回 null。 */
    UserConversation findOne(String ownerUserId, String conversationId);

    /** 查询用户全部会话，按 updatedAt 倒序 */
    List<UserConversation> findAll(String ownerUserId);

    /** 批量查询指定会话，不存在的跳过 */
    List<UserConversation> findByIds(String ownerUserId, List<String> conversationIds);

    /** 获取用户所有会话 ID */
    List<String> findConversationIds(String ownerUserId);

    /**
     * 查询指定用户集合中，已经存在该会话记录的用户 ID。
     */
    List<String> findExistingOwnerUserIds(List<String> ownerUserIds, String conversationId);

    /**
     * 从候选用户中找出对该会话设置了 NOT_RECEIVE 的用户 ID。
     * 离线推送过滤时使用。
     */
    List<String> findNotReceiveUserIds(String conversationId, List<String> candidateUserIds);

    /**
     * 查询会话下全部设置为 NOT_RECEIVE 的用户 ID。
     */
    List<String> findAllNotReceiveUserIds(String conversationId);

    /**
     * 查询用户所有设置为 RECEIVE_NOT_NOTIFY 的会话 ID。
     */
    List<String> findNotNotifyConversationIds(String ownerUserId);

    /**
     * 查询用户置顶会话 ID 列表。
     */
    List<String> findPinnedConversationIds(String ownerUserId);
}
