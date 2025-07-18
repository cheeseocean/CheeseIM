package com.cheeseocean.im.message.utils;

import com.cheeseocean.im.common.constants.MessageConstants;
import org.apache.commons.lang3.StringUtils;

/**
 * 会话工具类
 * 
 * @author CheeseIM
 */
public class ConversationUtils {
    
    /**
     * 生成会话ID
     * 
     * @param sessionType 会话类型
     * @param sendID 发送者ID
     * @param recvID 接收者ID
     * @param groupID 群组ID
     * @return 会话ID
     */
    public static String generateConversationID(Integer sessionType, String sendID, String recvID, String groupID) {
        if (sessionType == null) {
            throw new IllegalArgumentException("会话类型不能为空");
        }
        
        switch (sessionType) {
            case MessageConstants.SESSION_TYPE_SINGLE:
                // 单聊：使用两个用户ID生成，保证顺序一致
                if (StringUtils.isBlank(sendID) || StringUtils.isBlank(recvID)) {
                    throw new IllegalArgumentException("单聊会话发送者ID和接收者ID不能为空");
                }
                // 按字典序排序，确保同一对用户的会话ID一致
                if (sendID.compareTo(recvID) < 0) {
                    return "single_" + sendID + "_" + recvID;
                } else {
                    return "single_" + recvID + "_" + sendID;
                }
                
            case MessageConstants.SESSION_TYPE_GROUP:
                // 群聊：使用群组ID
                if (StringUtils.isBlank(groupID)) {
                    throw new IllegalArgumentException("群聊会话群组ID不能为空");
                }
                return "group_" + groupID;
                
            case MessageConstants.SESSION_TYPE_NOTIFICATION:
                // 通知：使用发送者和接收者ID
                if (StringUtils.isBlank(sendID) || StringUtils.isBlank(recvID)) {
                    throw new IllegalArgumentException("通知会话发送者ID和接收者ID不能为空");
                }
                return "notification_" + sendID + "_" + recvID;
                
            default:
                throw new IllegalArgumentException("不支持的会话类型: " + sessionType);
        }
    }
    
    /**
     * 解析会话ID获取会话类型
     * 
     * @param conversationID 会话ID
     * @return 会话类型
     */
    public static Integer parseSessionType(String conversationID) {
        if (StringUtils.isBlank(conversationID)) {
            return null;
        }
        
        if (conversationID.startsWith("single_")) {
            return MessageConstants.SESSION_TYPE_SINGLE;
        } else if (conversationID.startsWith("group_")) {
            return MessageConstants.SESSION_TYPE_GROUP;
        } else if (conversationID.startsWith("notification_")) {
            return MessageConstants.SESSION_TYPE_NOTIFICATION;
        }
        
        return null;
    }
    
    /**
     * 从单聊会话ID中解析用户ID
     * 
     * @param conversationID 会话ID
     * @return 用户ID数组，[0]和[1]分别是两个用户ID
     */
    public static String[] parseSingleChatUserIDs(String conversationID) {
        if (StringUtils.isBlank(conversationID) || !conversationID.startsWith("single_")) {
            return null;
        }
        
        String userPart = conversationID.substring("single_".length());
        return userPart.split("_", 2);
    }
    
    /**
     * 从群聊会话ID中解析群组ID
     * 
     * @param conversationID 会话ID
     * @return 群组ID
     */
    public static String parseGroupID(String conversationID) {
        if (StringUtils.isBlank(conversationID) || !conversationID.startsWith("group_")) {
            return null;
        }
        
        return conversationID.substring("group_".length());
    }
    
    /**
     * 验证会话ID格式
     * 
     * @param conversationID 会话ID
     * @return 是否有效
     */
    public static boolean isValidConversationID(String conversationID) {
        if (StringUtils.isBlank(conversationID)) {
            return false;
        }
        
        return conversationID.startsWith("single_") || 
               conversationID.startsWith("group_") || 
               conversationID.startsWith("notification_");
    }
}
