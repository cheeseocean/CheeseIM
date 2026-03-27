package com.cheeseocean.im.common.api.conversation;

/**
 * 会话生命周期管理 Dubbo 服务接口。
 * 由 social 模块实现，postmaster 模块调用。
 */
public interface ConversationSyncService {

    /**
     * 首条消息时为所有参与者懒创建会话记录（{@code cmd.newConversation() == true}）。
     * 必须在投递推送之前调用。
     */
    void createIfNew(ConversationSyncCommand cmd);

    /**
     * 同步一批消息后的会话状态（最新消息、未读数）。
     * 在 IngressEventListener 完成投递后调用。
     */
    void sync(ConversationSyncCommand cmd);

    /**
     * 标记会话已读：重置未读计数，并异步将 readSeq 持久化到 MongoDB。
     */
    void markRead(String ownerUserId, String conversationId, long readSeq);
}
