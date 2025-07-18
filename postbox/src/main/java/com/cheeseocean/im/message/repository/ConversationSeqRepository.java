package com.cheeseocean.im.message.repository;

import com.cheeseocean.im.message.entity.ConversationSeq;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 会话序列号存储库
 * 
 * @author CheeseIM
 */
@Repository
public interface ConversationSeqRepository extends MongoRepository<ConversationSeq, String> {
    
    /**
     * 根据会话ID查找序列号记录
     */
    Optional<ConversationSeq> findByConversationID(String conversationID);
    
    /**
     * 原子性增加序列号
     */
    @Query("{'conversationID': ?0}")
    @Update("{'$inc': {'seq': 1, 'maxSeq': 1}, '$set': {'updateTime': ?1}}")
    void incrementSeq(String conversationID, Long updateTime);
    
    /**
     * 更新最大序列号
     */
    @Query("{'conversationID': ?0}")
    @Update("{'$set': {'maxSeq': ?1, 'updateTime': ?2}}")
    void updateMaxSeq(String conversationID, Long maxSeq, Long updateTime);
    
    /**
     * 删除会话序列号记录
     */
    void deleteByConversationID(String conversationID);
}
