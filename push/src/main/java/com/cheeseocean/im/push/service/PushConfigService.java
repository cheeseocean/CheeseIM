package com.cheeseocean.im.push.service;

/**
 * 推送配置服务接口
 * 管理用户的推送相关配置，包括群聊读取类型等
 * 
 * @author CheeseIM
 */
public interface PushConfigService {
    
    /**
     * 群聊读取类型枚举
     */
    enum GroupChatReadType {
        /**
         * 读取所有群消息 - 所有群消息都会推送
         */
        READ_ALL(0),
        
        /**
         * 只读取@消息 - 只有@消息才会推送
         */
        READ_MENTION_ONLY(1),
        
        /**
         * 不读取群消息 - 群消息不会推送
         */
        READ_NONE(2);
        
        private final int value;
        
        GroupChatReadType(int value) {
            this.value = value;
        }
        
        public int getValue() {
            return value;
        }
        
        public static GroupChatReadType fromValue(int value) {
            for (GroupChatReadType type : values()) {
                if (type.value == value) {
                    return type;
                }
            }
            return READ_ALL; // 默认值
        }
    }
    
    /**
     * 获取用户的群聊读取类型
     * 
     * @param userID 用户ID
     * @param groupID 群组ID（可选，用于群组特定设置）
     * @return 群聊读取类型
     */
    GroupChatReadType getUserGroupChatReadType(String userID, String groupID);
    
    /**
     * 设置用户的群聊读取类型
     * 
     * @param userID 用户ID
     * @param groupID 群组ID（可选，用于群组特定设置）
     * @param readType 群聊读取类型
     * @return 是否设置成功
     */
    boolean setUserGroupChatReadType(String userID, String groupID, GroupChatReadType readType);
    
    /**
     * 获取用户的全局群聊读取类型（不针对特定群组）
     * 
     * @param userID 用户ID
     * @return 群聊读取类型
     */
    GroupChatReadType getUserGlobalGroupChatReadType(String userID);
    
    /**
     * 设置用户的全局群聊读取类型
     * 
     * @param userID 用户ID
     * @param readType 群聊读取类型
     * @return 是否设置成功
     */
    boolean setUserGlobalGroupChatReadType(String userID, GroupChatReadType readType);
    
    /**
     * 检查用户是否启用了推送
     * 
     * @param userID 用户ID
     * @return 是否启用推送
     */
    boolean isPushEnabled(String userID);
    
    /**
     * 检查用户是否启用了离线推送
     * 
     * @param userID 用户ID
     * @return 是否启用离线推送
     */
    boolean isOfflinePushEnabled(String userID);
    
    /**
     * 检查用户在指定群组中是否启用了推送
     * 
     * @param userID 用户ID
     * @param groupID 群组ID
     * @return 是否启用推送
     */
    boolean isGroupPushEnabled(String userID, String groupID);
    
    /**
     * 获取系统默认的群聊读取类型
     * 
     * @return 默认群聊读取类型
     */
    GroupChatReadType getDefaultGroupChatReadType();
}
