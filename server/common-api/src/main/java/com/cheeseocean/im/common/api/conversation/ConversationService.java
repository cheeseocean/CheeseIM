package com.cheeseocean.im.common.api.conversation;

import com.cheeseocean.im.common.api.business.domain.UserConversation;
import com.cheeseocean.im.common.api.dto.conversation.ConversationIncrementalSyncResult;
import com.cheeseocean.im.common.api.dto.conversation.SetConversationRequest;

import java.util.List;

/**
 * 会话统一服务接口。
 *
 * <p>统一承载会话查询、会话创建和会话配置更新能力，
 * 作为对外暴露的唯一 Dubbo 合约。
 */
public interface ConversationService {

    /**
     * 查询用户在指定会话下的会话配置。
     * 不存在时返回 {@code null}。
     */
    UserConversation getConversation(String ownerUserId, String conversationId);

    /**
     * 批量查询用户在指定会话集合下的会话配置。
     * 返回顺序与入参会话 ID 顺序保持一致。
     */
    List<UserConversation> getConversations(String ownerUserId, List<String> conversationIds);

    /**
     * 查询用户全部会话，并按更新时间倒序返回。
     */
    List<UserConversation> getAllConversations(String ownerUserId);

    /**
     * 查询用户全部会话 ID。
     */
    List<String> getConversationIds(String ownerUserId);

    /**
     * 计算用户会话 ID 集合的稳定 hash 值。
     * 用于客户端判断会话列表是否发生变化。
     */
    long getConversationIdsHash(String ownerUserId);

    /**
     * 查询用户全部免提醒会话 ID。
     */
    List<String> getNotNotifyConversationIds(String ownerUserId);

    /**
     * 查询用户全部置顶会话 ID。
     */
    List<String> getPinnedConversationIds(String ownerUserId);

    /**
     * 按用户会话版本同步会话元数据。
     *
     * <p>版本有效时返回增量变更；版本缺失、版本流不匹配或 hash 不可信时返回全量结果。
     */
    ConversationIncrementalSyncResult syncConversations(String ownerUserId, String versionId, long version, long idHash);

    /**
     * 查询用户在指定会话中的接收选项。
     * 未创建会话记录时默认返回正常接收。
     */
    int getReceiveOption(String ownerUserId, String conversationId);

    /**
     * 从候选用户中筛出允许离线推送的用户列表。
     */
    List<String> getOfflinePushUserIds(String conversationId, List<String> candidateUserIds);

    /**
     * 创建单聊或通知会话。
     */
    void createSingleChatConversation(String senderId, String recvId, String conversationId, int conversationType);

    /**
     * 为群成员批量创建群会话。
     */
    void createGroupChatConversations(String groupId, String conversationId, List<String> userIds);

    /**
     * 批量设置会话配置。
     */
    void setConversations(List<String> userIds, SetConversationRequest request);

    /**
     * 删除当前用户维度的会话元数据。
     *
     * <p>该操作不删除历史消息，也不影响其他参与者；客户端通过会话版本增量同步收到 delete 后移除本地会话。
     */
    void deleteConversation(String ownerUserId, String conversationId);
}
