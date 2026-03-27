package com.cheeseocean.im.common.api.conversation;

import com.cheeseocean.im.common.api.dto.conversation.ConversationDTO;

import java.util.List;

/**
 * 会话查询 Dubbo 服务接口。
 *
 * <p>提供用户维度的会话读取能力，对应 OpenIM conversationServer 中的查询类接口：
 * GetConversation / GetConversations / GetAllConversations /
 * GetConversationIDs / GetUserConversationIDsHash / GetConversationOfflinePushUserIDs。
 */
public interface ConversationQueryService {

    /**
     * 查询用户的单条会话。
     * 会话不存在时返回 null。
     */
    ConversationDTO getConversation(String ownerUserId, String conversationId);

    /**
     * 批量查询用户的指定会话列表。
     * 不存在的会话直接跳过（不抛异常）。
     */
    List<ConversationDTO> getConversations(String ownerUserId, List<String> conversationIds);

    /**
     * 查询用户所有会话。
     * 结果按 updatedAt 倒序排列。
     */
    List<ConversationDTO> getAllConversations(String ownerUserId);

    /**
     * 获取用户所有会话 ID 列表。
     * 客户端首次拉取或全量同步时使用。
     */
    List<String> getConversationIds(String ownerUserId);

    /**
     * 获取用户会话 ID 列表的哈希值。
     * 客户端通过对比本地哈希决定是否需要全量拉取，减少不必要的流量。
     */
    long getConversationIdsHash(String ownerUserId);

    /**
     * 从候选用户列表中过滤出需要接收离线推送的用户 ID。
     *
     * <p>实现：从候选列表中去除对该会话设置了 NOT_RECEIVE 的用户，
     * 返回剩余可推送的用户 ID 列表。
     * 被 postman 模块在发起离线推送前调用。
     *
     * @param conversationId   目标会话 ID
     * @param candidateUserIds 推送候选用户 ID 列表
     * @return 可接收离线推送的用户 ID 列表
     */
    List<String> getOfflinePushUserIds(String conversationId, List<String> candidateUserIds);
}
