package com.cheeseocean.im.common.api.conversation;

import com.cheeseocean.im.common.api.dto.conversation.SetConversationRequest;

import java.util.List;

/**
 * 会话写入 Dubbo 服务接口。
 *
 * <p>提供会话的显式创建和配置更新能力，对应 OpenIM conversationServer 中的写类接口：
 * CreateSingleChatConversations / CreateGroupChatConversations / SetConversations。
 *
 * <p>注意：消息路径上的懒创建由 {@link ConversationSyncService#createIfNew} 负责，
 * 本接口用于客户端主动发起的预建会话或配置变更场景。
 */
public interface ConversationWriteService {

    /**
     * 显式创建单聊或通知类会话（写扩散）。
     *
     * <p>单聊：分别为 senderId 和 recvId 各创建一条会话记录，双向写扩散。
     * 通知：仅为 recvId 创建一条记录（系统通知账号发出，无需反向）。
     * 已存在时幂等，不重复插入。
     *
     * @param senderId       发起方用户 ID
     * @param recvId         接收方用户 ID
     * @param conversationId 会话 ID
     * @param conversationType 会话类型（1=单聊，3=通知）
     */
    void createSingleChatConversation(String senderId, String recvId,
                                      String conversationId, int conversationType);

    /**
     * 为群组内的所有成员批量创建群聊会话（写扩散）。
     *
     * <p>为 userIds 中的每个用户创建一条 ownerUserId=userId、targetId=groupId 的会话记录。
     * 已存在时幂等，不重复插入。
     *
     * @param groupId        群组 ID
     * @param conversationId 会话 ID（通常为 g_{groupId}）
     * @param userIds        需要创建会话的用户 ID 列表
     */
    void createGroupChatConversations(String groupId, String conversationId, List<String> userIds);

    /**
     * 批量设置用户会话配置（upsert 语义）。
     *
     * <p>对每个 userId：若会话记录不存在则创建，存在则仅更新 request 中非 null 的字段。
     * 典型用途：客户端修改置顶、免打扰、草稿等会话属性。
     *
     * @param userIds 需要更新的用户 ID 列表
     * @param request 会话配置，仅非 null 字段参与更新
     */
    void setConversations(List<String> userIds, SetConversationRequest request);
}
