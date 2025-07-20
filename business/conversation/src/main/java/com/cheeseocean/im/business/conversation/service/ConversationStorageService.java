package com.cheeseocean.im.business.conversation.service;

import com.cheeseocean.im.business.conversation.api.param.Conversation;
import com.cheeseocean.im.business.conversation.entity.VersionLogMongo;

import java.util.List;
import java.util.Map;

/**
 * 会话存储服务接口 - 严格按照OpenIM的ConversationDatabase接口设计
 * 结合Redis缓存和MongoDB持久化存储的二级缓存架构
 * 返回业务层Conversation对象，而不是数据库层ConversationMongo对象
 *
 * @author CheeseIM
 */
public interface ConversationStorageService {

    /**
     * 更新指定用户的会话字段
     * 对应OpenIM的UpdateUsersConversationField方法
     *
     * @param userIDs 用户ID列表
     * @param conversationID 会话ID
     * @param args 更新字段映射
     * @return 是否成功
     */
    Boolean updateUsersConversationField(List<String> userIDs, String conversationID, Map<String, Object> args);

    /**
     * 创建批量新会话
     * 对应OpenIM的CreateConversation方法
     *
     * @param conversations 会话列表
     * @return 是否成功
     */
    Boolean createConversation(List<Conversation> conversations);

    /**
     * 同步对等用户私聊会话（事务操作）
     * 对应OpenIM的SyncPeerUserPrivateConversationTx方法
     *
     * @param conversations 会话列表
     * @return 是否成功
     */
    Boolean syncPeerUserPrivateConversationTx(List<Conversation> conversations);

    /**
     * 根据会话ID检索用户的多个会话
     * 对应OpenIM的FindConversations方法
     *
     * @param ownerUserID 会话所有者用户ID
     * @param conversationIDs 会话ID列表
     * @return 会话列表
     */
    List<Conversation> findConversations(String ownerUserID, List<String> conversationIDs);

    /**
     * 获取用户在服务器上的所有会话
     * 对应OpenIM的GetUserAllConversation方法
     *
     * @param ownerUserID 会话所有者用户ID
     * @return 会话列表
     */
    List<Conversation> getUserAllConversation(String ownerUserID);

    /**
     * 为用户设置多个会话属性，如果不存在则创建新会话，否则更新。此操作是原子的
     * 对应OpenIM的SetUserConversations方法
     *
     * @param ownerUserID 会话所有者用户ID
     * @param conversations 会话列表
     * @return 是否成功
     */
    Boolean setUserConversations(String ownerUserID, List<Conversation> conversations);

    /**
     * 为多个用户的会话更新特定字段，如果不存在则创建新会话，否则更新。此操作是事务性的
     * 对应OpenIM的SetUsersConversationFieldTx方法
     *
     * @param userIDs 用户ID列表
     * @param conversation 会话对象
     * @param fieldMap 字段映射
     * @return 是否成功
     */
    Boolean setUsersConversationFieldTx(List<String> userIDs, Conversation conversation, Map<String, Object> fieldMap);

    /**
     * 更新与指定用户相关的所有会话
     * 此函数不更新用户自己的会话，而是更新引用此用户的其他用户的会话
     * 对应OpenIM的UpdateUserConversations方法
     *
     * @param userID 用户ID
     * @param args 更新参数
     * @return 是否成功
     */
    Boolean updateUserConversations(String userID, Map<String, Object> args);

    /**
     * 为指定的群组ID和用户ID创建群聊会话
     * 对应OpenIM的CreateGroupChatConversation方法
     *
     * @param groupID 群组ID
     * @param userIDs 用户ID列表
     * @param conversation 会话对象
     * @return 是否成功
     */
    Boolean createGroupChatConversation(String groupID, List<String> userIDs, Conversation conversation);

    /**
     * 检索给定用户的会话ID
     * 对应OpenIM的GetConversationIDs方法
     *
     * @param userID 用户ID
     * @return 会话ID列表
     */
    List<String> getConversationIDs(String userID);

    /**
     * 获取给定用户的会话ID哈希值
     * 对应OpenIM的GetUserConversationIDsHash方法
     *
     * @param ownerUserID 会话所有者用户ID
     * @return 哈希值
     */
    Long getUserConversationIDsHash(String ownerUserID);

    /**
     * 获取所有会话ID
     * 对应OpenIM的GetAllConversationIDs方法
     *
     * @return 所有会话ID列表
     */
    List<String> getAllConversationIDs();

    /**
     * 返回所有会话ID的数量
     * 对应OpenIM的GetAllConversationIDsNumber方法
     *
     * @return 会话ID数量
     */
    Long getAllConversationIDsNumber();

    /**
     * 根据指定的分页设置分页浏览会话ID
     * 对应OpenIM的PageConversationIDs方法
     *
     * @param pageNumber 页码
     * @param showNumber 每页显示数量
     * @return 会话ID列表
     */
    List<String> pageConversationIDs(Integer pageNumber, Integer showNumber);

    /**
     * 根据会话ID检索会话
     * 对应OpenIM的GetConversationsByConversationID方法
     *
     * @param conversationIDs 会话ID列表
     * @return 会话列表
     */
    List<Conversation> getConversationsByConversationID(List<String> conversationIDs);

    /**
     * 根据特定条件获取需要销毁的会话
     * 对应OpenIM的GetConversationIDsNeedDestruct方法
     *
     * @return 需要销毁的会话列表
     */
    List<Conversation> getConversationIDsNeedDestruct();

    /**
     * 获取会话中未接收消息的用户ID
     * 对应OpenIM的GetConversationNotReceiveMessageUserIDs方法
     *
     * @param conversationID 会话ID
     * @return 未接收消息的用户ID列表
     */
    List<String> getConversationNotReceiveMessageUserIDs(String conversationID);

    /**
     * 查找会话用户版本
     * 对应OpenIM的FindConversationUserVersion方法
     *
     * @param userID 用户ID
     * @param version 版本号
     * @param limit 限制数量
     * @return 版本日志
     */
    VersionLogMongo findConversationUserVersion(String userID, Long version, Integer limit);

    /**
     * 查找最大会话用户版本缓存
     * 对应OpenIM的FindMaxConversationUserVersionCache方法
     *
     * @param userID 用户ID
     * @return 版本日志
     */
    VersionLogMongo findMaxConversationUserVersionCache(String userID);

    /**
     * 获取用户的会话（分页）
     * 对应OpenIM的GetOwnerConversation方法
     *
     * @param ownerUserID 会话所有者用户ID
     * @param pageNumber 页码
     * @param showNumber 每页显示数量
     * @return 会话分页结果
     */
    ConversationPage getOwnerConversation(String ownerUserID, Integer pageNumber, Integer showNumber);

    /**
     * 根据用户ID获取不通知的会话ID
     * 对应OpenIM的GetNotNotifyConversationIDs方法
     *
     * @param userID 用户ID
     * @return 不通知的会话ID列表
     */
    List<String> getNotNotifyConversationIDs(String userID);

    /**
     * 根据用户ID获取置顶的会话ID
     * 对应OpenIM的GetPinnedConversationIDs方法
     *
     * @param userID 用户ID
     * @return 置顶的会话ID列表
     */
    List<String> getPinnedConversationIDs(String userID);

    /**
     * 根据指定的时间戳和限制查找随机会话
     * 对应OpenIM的FindRandConversation方法
     *
     * @param ts 时间戳
     * @param limit 限制数量
     * @return 随机会话列表
     */
    List<Conversation> findRandConversation(Long ts, Integer limit);

    /**
     * 会话分页结果
     */
    class ConversationPage {
        private List<Conversation> conversations;
        private Long total;

        public ConversationPage(List<Conversation> conversations, Long total) {
            this.conversations = conversations;
            this.total = total;
        }

        public List<Conversation> getConversations() {
            return conversations;
        }

        public void setConversations(List<Conversation> conversations) {
            this.conversations = conversations;
        }

        public Long getTotal() {
            return total;
        }

        public void setTotal(Long total) {
            this.total = total;
        }
    }


}
