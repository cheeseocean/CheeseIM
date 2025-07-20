package com.cheeseocean.im.business.conversation.repository;

import com.cheeseocean.im.business.conversation.entity.ConversationMongo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 会话存储库 - 严格按照OpenIM的ConversationDatabase接口设计
 *
 * @author CheeseIM
 */
@Repository
public interface ConversationRepository extends MongoRepository<ConversationMongo, String> {

    /**
     * 根据所有者用户ID和会话ID查找会话
     */
    Optional<ConversationMongo> findByOwnerUserIDAndConversationID(String ownerUserID, String conversationID);

    /**
     * 根据所有者用户ID和会话ID列表查找会话
     */
    List<ConversationMongo> findByOwnerUserIDAndConversationIDIn(String ownerUserID, List<String> conversationIDs);

    /**
     * 根据所有者用户ID查找所有会话
     */
    List<ConversationMongo> findByOwnerUserID(String ownerUserID);

    /**
     * 根据所有者用户ID查找会话（分页）
     */
    Page<ConversationMongo> findByOwnerUserID(String ownerUserID, Pageable pageable);

    /**
     * 根据所有者用户ID统计会话数量
     */
    long countByOwnerUserID(String ownerUserID);

    /**
     * 根据所有者用户ID获取会话ID列表
     */
    @Query(value = "{'owner_user_id': ?0}", fields = "{'conversation_id': 1}")
    List<ConversationMongo> findConversationIDsByOwnerUserID(String ownerUserID);

    /**
     * 根据会话ID列表查找会话
     */
    List<ConversationMongo> findByConversationIDIn(List<String> conversationIDs);

    /**
     * 根据会话ID查找不接收消息的用户
     */
    @Query("{'conversation_id': ?0, 'recv_msg_opt': {'$ne': 0}}")
    List<ConversationMongo> findNotReceiveMessageUsers(String conversationID);

    /**
     * 根据用户ID查找不通知的会话ID
     */
    @Query(value = "{'owner_user_id': ?0, 'recv_msg_opt': {'$in': [1, 2]}}", fields = "{'conversation_id': 1}")
    List<ConversationMongo> findNotNotifyConversationIDs(String userID);

    /**
     * 根据用户ID查找置顶的会话ID
     */
    @Query(value = "{'owner_user_id': ?0, 'is_pinned': true}", fields = "{'conversation_id': 1}")
    List<ConversationMongo> findPinnedConversationIDs(String userID);

    /**
     * 查找需要销毁的会话
     */
    @Query("{'is_msg_destruct': true, 'msg_destruct_time': {'$gt': 0}}")
    List<ConversationMongo> findConversationsNeedDestruct();

    /**
     * 根据时间戳查找随机会话
     */
    @Query("{'_id': {'$gte': ?0}}")
    List<ConversationMongo> findRandConversationByTimestamp(Long ts, Pageable pageable);

    /**
     * 批量更新用户会话字段
     */
    @Query("{'owner_user_id': {'$in': ?0}, 'conversation_id': ?1}")
    @Update("{'$set': ?2}")
    void updateUsersConversationField(List<String> userIDs, String conversationID, Object updateFields);

    /**
     * 更新用户相关的所有会话
     */
    @Query("{'user_id': ?0}")
    @Update("{'$set': ?1}")
    void updateUserConversations(String userID, Object updateFields);

    /**
     * 根据群组ID查找会话
     */
    List<ConversationMongo> findByGroupID(String groupID);

    /**
     * 检查会话是否存在
     */
    boolean existsByOwnerUserIDAndConversationID(String ownerUserID, String conversationID);

    /**
     * 删除用户会话
     */
    void deleteByOwnerUserIDAndConversationID(String ownerUserID, String conversationID);

    /**
     * 根据用户ID删除所有会话
     */
    void deleteByOwnerUserID(String ownerUserID);

    /**
     * 获取所有会话ID
     */
    @Query(value = "{}", fields = "{'conversation_id': 1}")
    List<ConversationMongo> findAllConversationIDs();

    /**
     * 统计所有会话ID数量
     */
    @Query(value = "{}", count = true)
    long countAllConversationIDs();

    /**
     * 分页获取会话ID
     */
    @Query(value = "{}", fields = "{'conversation_id': 1}")
    Page<ConversationMongo> findAllConversationIDsWithPage(Pageable pageable);
}
