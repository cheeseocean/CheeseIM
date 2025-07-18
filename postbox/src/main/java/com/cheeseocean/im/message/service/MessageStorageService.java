package com.cheeseocean.im.message.service;

import com.cheeseocean.im.common.entity.Message;
import com.cheeseocean.im.message.entity.MessageMongo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * 消息存储服务接口
 * 
 * @author CheeseIM
 */
public interface MessageStorageService {
    
    /**
     * 保存消息
     */
    MessageMongo saveMessage(Message message);
    
    /**
     * 根据服务端消息ID查找消息
     */
    Optional<MessageMongo> findByServerMsgID(String serverMsgID);
    
    /**
     * 根据客户端消息ID查找消息
     */
    Optional<MessageMongo> findByClientMsgID(String clientMsgID);
    
    /**
     * 获取会话消息历史（分页）
     */
    Page<MessageMongo> getConversationHistory(String conversationID, Pageable pageable);
    
    /**
     * 根据序列号范围获取消息
     */
    List<MessageMongo> getMessagesBySeqRange(String conversationID, Long startSeq, Long endSeq);
    
    /**
     * 获取单聊消息历史
     */
    Page<MessageMongo> getSingleChatHistory(String userID1, String userID2, Pageable pageable);
    
    /**
     * 获取群聊消息历史
     */
    Page<MessageMongo> getGroupChatHistory(String groupID, Pageable pageable);
    
    /**
     * 搜索消息
     */
    Page<MessageMongo> searchMessages(String keyword, Pageable pageable);
    
    /**
     * 搜索用户相关消息
     */
    Page<MessageMongo> searchUserMessages(String userID, String keyword, Pageable pageable);
    
    /**
     * 获取会话最新消息
     */
    Optional<MessageMongo> getLatestMessage(String conversationID);
    
    /**
     * 获取会话最大序列号
     */
    Long getMaxSeq(String conversationID);
    
    /**
     * 生成会话序列号
     */
    Long generateSeq(String conversationID);
    
    /**
     * 统计会话消息数量
     */
    long countConversationMessages(String conversationID);
    
    /**
     * 统计用户未读消息数量
     */
    long countUnreadMessages(String userID);
    
    /**
     * 标记消息为已读
     */
    void markMessageAsRead(String serverMsgID);
    
    /**
     * 批量标记消息为已读
     */
    void markMessagesAsRead(List<String> serverMsgIDs);
    
    /**
     * 删除消息
     */
    void deleteMessage(String serverMsgID);
    
    /**
     * 删除会话所有消息
     */
    void deleteConversationMessages(String conversationID);
    
    /**
     * 清理过期消息
     */
    void cleanExpiredMessages(Long expireTime);
}
