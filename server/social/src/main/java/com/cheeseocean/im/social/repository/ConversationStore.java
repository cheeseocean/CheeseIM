package com.cheeseocean.im.social.repository;

import com.cheeseocean.im.social.model.Conversation;

import java.util.List;

/**
 * 用户会话记录（MongoDB）的持久化接口。
 * 标注为幂等的方法均可安全重试。
 */
public interface ConversationStore {

    /**
     * 若 (ownerUserId, conversationId) 对应的会话记录不存在则插入，已存在则忽略。幂等。
     */
    void createIfAbsent(Conversation conversation);

    /**
     * 更新指定会话的最新消息摘要和 seq。
     */
    void updateLatestMessage(String ownerUserId, String conversationId,
                             long latestMsgSeq, String latestMsgJson);

    /**
     * 原子地将未读计数加 {@code delta}。
     * delta 通常为该批次中非本人发送的消息数量。
     */
    void incrementUnread(String ownerUserId, String conversationId, int delta);

    /** 将未读计数重置为 0，并记录已确认的 readSeq。 */
    void clearUnread(String ownerUserId, String conversationId, long readSeq);

    /**
     * 返回指定会话的 recvMsgOpt code。
     * 会话记录不存在时返回 {@link com.cheeseocean.im.common.core.enums.RecvMsgOpt#RECEIVE RECEIVE}（0）。
     */
    int getRecvMsgOpt(String ownerUserId, String conversationId);

    /**
     * 覆写指定会话的 recvMsgOpt。
     *
     * @param recvMsgOpt {@link com.cheeseocean.im.common.core.enums.RecvMsgOpt} 的整数 code
     */
    void setRecvMsgOpt(String ownerUserId, String conversationId, int recvMsgOpt);

    // ── 查询方法 ──────────────────────────────────────────────────────────────

    /**
     * 查询用户的单条会话，不存在时返回 null。
     */
    Conversation findOne(String ownerUserId, String conversationId);

    /**
     * 查询用户的所有会话，按 updatedAt 倒序排列。
     */
    List<Conversation> findAll(String ownerUserId);

    /**
     * 批量查询用户的指定会话列表，不存在的跳过。
     */
    List<Conversation> findByIds(String ownerUserId, List<String> conversationIds);

    /**
     * 获取用户所有会话 ID 列表。
     */
    List<String> findConversationIds(String ownerUserId);

    /**
     * 从候选用户列表中找出对该会话设置了 NOT_RECEIVE 的用户 ID。
     * 离线推送过滤时使用：调用方从候选列表中排除这些用户。
     */
    List<String> findNotReceiveUserIds(String conversationId, List<String> candidateUserIds);

    /**
     * 按字段 map upsert 更新指定会话配置（createIfAbsent + 更新可选字段）。
     */
    void upsertFields(String ownerUserId, String conversationId,
                      int conversationType, String targetId,
                      java.util.Map<String, Object> fields);
}
