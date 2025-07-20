package com.cheeseocean.im.common.service;

import com.cheeseocean.im.common.entity.conversation.GetAllConversationsReq;
import com.cheeseocean.im.common.entity.conversation.GetAllConversationsResp;
import com.cheeseocean.im.common.entity.conversation.SetConversationReq;
import com.cheeseocean.im.common.entity.conversation.SetConversationResp;

/**
 * 会话服务接口 - 严格按照OpenIM的ConversationServer接口设计
 * 对应OpenIM的conversation RPC服务
 *
 * @author CheeseIM
 */
public interface ConversationService {

    /**
     * 获取用户的分页会话列表
     * 对应OpenIM的get_owner_conversation接口
     *
     * @param request 获取会话请求
     * @return 获取会话响应
     */
    GetAllConversationsResp getOwnerConversation(GetAllConversationsReq request);

    /**
     * 为多个用户设置同一会话的字段
     * 对应OpenIM的set_conversations接口
     *
     * @param request 设置会话请求
     * @return 设置会话响应
     */
    SetConversationResp setConversations(SetConversationReq request);

    /**
     * 获取排序的会话列表
     * 对应OpenIM的get_sorted_conversation_list接口
     *
     * @param request 获取排序会话请求
     * @return 获取排序会话响应
     */
    GetAllConversationsResp getSortedConversationList(GetAllConversationsReq request);
}
