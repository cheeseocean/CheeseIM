package com.cheeseocean.im.message.repository;

import com.cheeseocean.im.message.entity.MessageMongo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 消息存储库
 * 
 * @author CheeseIM
 */
@Repository
public interface MessageRepository extends MongoRepository<MessageMongo, String> {
    
    /**
     * 根据服务端消息ID查找消息
     */
    Optional<MessageMongo> findByServerMsgID(String serverMsgID);
    
    /**
     * 根据客户端消息ID查找消息
     */
    Optional<MessageMongo> findByClientMsgID(String clientMsgID);
    
    /**
     * 根据会话ID查找消息列表（分页）
     */
    Page<MessageMongo> findByConversationIDOrderBySeqDesc(String conversationID, Pageable pageable);
    
    /**
     * 根据会话ID和序列号范围查找消息
     */
    @Query("{'conversationID': ?0, 'seq': {'$gte': ?1, '$lte': ?2}}")
    List<MessageMongo> findByConversationIDAndSeqBetween(String conversationID, Long startSeq, Long endSeq);
    
    /**
     * 根据发送者和接收者查找消息（单聊）
     */
    @Query("{'$or': [{'sendID': ?0, 'recvID': ?1}, {'sendID': ?1, 'recvID': ?0}], 'sessionType': 1}")
    Page<MessageMongo> findSingleChatMessages(String userID1, String userID2, Pageable pageable);
    
    /**
     * 根据群组ID查找消息（群聊）
     */
    Page<MessageMongo> findByGroupIDAndSessionTypeOrderBySendTimeDesc(String groupID, Integer sessionType, Pageable pageable);
    
    /**
     * 根据发送者ID查找消息
     */
    Page<MessageMongo> findBySendIDOrderBySendTimeDesc(String sendID, Pageable pageable);
    
    /**
     * 根据接收者ID查找消息
     */
    Page<MessageMongo> findByRecvIDOrderBySendTimeDesc(String recvID, Pageable pageable);
    
    /**
     * 根据会话ID查找最新消息
     */
    @Query(value = "{'conversationID': ?0}", sort = "{'seq': -1}")
    Optional<MessageMongo> findLatestMessageByConversationID(String conversationID);
    
    /**
     * 根据会话ID查找最大序列号
     */
    @Query(value = "{'conversationID': ?0}", fields = "{'seq': 1}", sort = "{'seq': -1}")
    Optional<MessageMongo> findMaxSeqByConversationID(String conversationID);
    
    /**
     * 根据会话ID和序列号查找消息
     */
    Optional<MessageMongo> findByConversationIDAndSeq(String conversationID, Long seq);
    
    /**
     * 根据时间范围查找消息
     */
    @Query("{'sendTime': {'$gte': ?0, '$lte': ?1}}")
    List<MessageMongo> findByTimeRange(Long startTime, Long endTime);
    
    /**
     * 根据内容搜索消息
     */
    @Query("{'content': {'$regex': ?0, '$options': 'i'}}")
    Page<MessageMongo> findByContentContaining(String keyword, Pageable pageable);
    
    /**
     * 根据用户ID搜索相关消息
     */
    @Query("{'$or': [{'sendID': ?0}, {'recvID': ?0}], 'content': {'$regex': ?1, '$options': 'i'}}")
    Page<MessageMongo> findUserMessagesWithKeyword(String userID, String keyword, Pageable pageable);
    
    /**
     * 统计会话消息数量
     */
    long countByConversationID(String conversationID);
    
    /**
     * 统计未读消息数量
     */
    @Query(value = "{'recvID': ?0, 'isRead': false}", count = true)
    long countUnreadMessages(String userID);
    
    /**
     * 删除会话的所有消息
     */
    void deleteByConversationID(String conversationID);
    
    /**
     * 根据时间删除过期消息
     */
    @Query(value = "{'createTime': {'$lt': ?0}}", delete = true)
    void deleteExpiredMessages(Long expireTime);
}
