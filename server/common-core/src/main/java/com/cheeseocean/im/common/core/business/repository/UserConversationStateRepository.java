package com.cheeseocean.im.common.core.business.repository;

import com.cheeseocean.im.common.core.business.domain.UserConversationState;

import java.util.List;
import java.util.Map;

/**
 * 用户-会话业务状态仓储抽象接口。
 *
 * <p>管理 {@link UserConversationState} 的持久化，
 * 仅涵盖业务配置类字段（置顶、免打扰、草稿等）以及少量兼容性展示字段。
 * 序列号同步字段（maxSeq / minSeq / readSeq）由偏移量仓储负责。
 */
public interface UserConversationStateRepository {

    /**
     * 若会话记录不存在则插入，已存在则忽略（幂等）。
     */
    void createIfAbsent(UserConversationState state);

    /**
     * 更新最新消息摘要和序列号。
     *
     * <p>当前查询链已优先从消息域读取 latest summary；
     * 该接口保留给兼容旧数据或回滚场景使用。
     */
    void updateLatestMessage(String ownerUserId, String conversationId,
                             long latestMsgSeq, String latestMsgJson);

    /**
     * 原子递增未读计数。
     *
     * <p>当前查询链已优先通过 {@code maxSeq - readSeq} 计算未读；
     * 该接口保留给兼容旧数据或回滚场景使用。
     */
    void incrementUnread(String ownerUserId, String conversationId, int delta);

    /**
     * 重置未读计数为 0。
     * 由标记已读流程触发，用于兼容旧字段；readSeq 的持久化由
     * {@link ConversationOffsetRangeRepository#updateReadSeq} 负责。
     */
    void clearUnread(String ownerUserId, String conversationId);

    /**
     * 查询指定会话的 recvMsgOpt，记录不存在时返回 0（正常接收）。
     */
    int getRecvMsgOpt(String ownerUserId, String conversationId);

    /**
     * 覆写指定会话的 recvMsgOpt。
     */
    void setRecvMsgOpt(String ownerUserId, String conversationId, int recvMsgOpt);

    /** 查询单条会话业务状态，不存在时返回 null */
    UserConversationState findOne(String ownerUserId, String conversationId);

    /** 查询用户全部会话，按 updatedAt 倒序 */
    List<UserConversationState> findAll(String ownerUserId);

    /** 批量查询指定会话，不存在的跳过 */
    List<UserConversationState> findByIds(String ownerUserId, List<String> conversationIds);

    /** 获取用户所有会话 ID */
    List<String> findConversationIds(String ownerUserId);

    /**
     * 从候选用户中找出对该会话设置了 NOT_RECEIVE 的用户 ID。
     * 离线推送过滤时使用。
     */
    List<String> findNotReceiveUserIds(String conversationId, List<String> candidateUserIds);

    /**
     * Upsert 更新会话的指定字段（createIfAbsent + 字段级更新）。
     *
     * @param fields key 为 BSON 字段名，value 为新值
     */
    void upsertFields(String ownerUserId, String conversationId,
                      int conversationType, String targetId,
                      Map<String, Object> fields);
}
