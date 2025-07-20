package com.cheeseocean.im.common.service;

import com.cheeseocean.im.common.entity.Message;

import java.util.List;

/**
 * 消息服务接口 - RPC接口
 *
 * @author CheeseIM
 */
public interface MessageService {

    /**
     * 发送消息
     *
     * @param request 发送消息请求
     * @return 发送消息响应
     */
    SendMsgResp sendMsg(SendMsgReq request);

    /**
     * 批量发送消息
     *
     * @param requests 发送消息请求列表
     * @return 发送消息响应列表
     */
    SendMsgResp[] batchSendMsg(SendMsgReq[] requests);

    /**
     * 获取会话消息历史
     *
     * @param conversationID 会话ID
     * @param startSeq 起始序列号
     * @param count 消息数量
     * @return 消息列表
     */
    List<Message> getConversationHistory(String conversationID, Long startSeq, Integer count);

    /**
     * 获取单聊消息历史
     *
     * @param userID1 用户1ID
     * @param userID2 用户2ID
     * @param startSeq 起始序列号
     * @param count 消息数量
     * @return 消息列表
     */
    List<Message> getSingleChatHistory(String userID1, String userID2, Long startSeq, Integer count);

    /**
     * 获取群聊消息历史
     *
     * @param groupID 群组ID
     * @param startSeq 起始序列号
     * @param count 消息数量
     * @return 消息列表
     */
    List<Message> getGroupChatHistory(String groupID, Long startSeq, Integer count);

    /**
     * 搜索消息
     *
     * @param userID 用户ID
     * @param keyword 关键词
     * @param page 页码
     * @param size 页大小
     * @return 消息列表
     */
    List<Message> searchMessages(String userID, String keyword, Integer page, Integer size);

    /**
     * 标记消息为已读
     *
     * @param userID 用户ID
     * @param serverMsgIDs 服务端消息ID列表
     * @return 是否成功
     */
    Boolean markMessagesAsRead(String userID, List<String> serverMsgIDs);

    /**
     * 撤回消息
     *
     * @param userID 用户ID
     * @param serverMsgID 服务端消息ID
     * @return 是否成功
     */
    Boolean revokeMessage(String userID, String serverMsgID);
}
