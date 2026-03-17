package com.cheeseocean.im.push.service;

import com.cheeseocean.im.common.entity.Message;
import com.cheeseocean.im.push.entity.PushMessage;

import java.util.List;

/**
 * 推送模板服务接口
 * 负责根据消息和用户信息创建推送消息
 * 
 * @author CheeseIM
 */
public interface PushTemplateService {
    
    /**
     * 为用户创建推送消息列表
     * 
     * @param userID 用户ID
     * @param title 推送标题
     * @param content 推送内容
     * @param originalMessage 原始消息
     * @return 推送消息列表（可能包含多个平台的推送消息）
     */
    List<PushMessage> createPushMessagesForUser(String userID, String title, String content, Message originalMessage);
    
    /**
     * 为指定平台创建推送消息
     * 
     * @param userID 用户ID
     * @param platformID 平台ID
     * @param title 推送标题
     * @param content 推送内容
     * @param originalMessage 原始消息
     * @return 推送消息
     */
    PushMessage createPushMessageForPlatform(String userID, Integer platformID, String title, String content, Message originalMessage);

    default PushMessage createPushMessage(String userID, Integer platformID, String title, String content, Message originalMessage) {
        return createPushMessageForPlatform(userID, platformID, title, content, originalMessage);
    }
    
    /**
     * 根据消息类型生成推送标题
     * 
     * @param message 原始消息
     * @return 推送标题
     */
    String generatePushTitle(Message message);
    
    /**
     * 根据消息类型生成推送内容
     * 
     * @param message 原始消息
     * @return 推送内容
     */
    String generatePushContent(Message message);
    
    /**
     * 获取用户的设备Token列表
     * 
     * @param userID 用户ID
     * @return 设备Token列表（包含平台信息）
     */
    List<DeviceTokenInfo> getUserDeviceTokens(String userID);

    default PushTemplate getPushTemplate(Integer messageType, Integer sessionType) {
        return new PushTemplate(messageType, sessionType);
    }

    default UserPushSettings getUserPushSettings(String userID) {
        return new UserPushSettings(userID, true);
    }
    
    /**
     * 设备Token信息类
     */
    class DeviceTokenInfo {
        private String deviceToken;
        private Integer platformID;
        private String deviceID;
        private Long lastActiveTime;
        
        public DeviceTokenInfo() {
        }
        
        public DeviceTokenInfo(String deviceToken, Integer platformID) {
            this.deviceToken = deviceToken;
            this.platformID = platformID;
        }
        
        // Getter and Setter methods
        public String getDeviceToken() {
            return deviceToken;
        }
        
        public void setDeviceToken(String deviceToken) {
            this.deviceToken = deviceToken;
        }
        
        public Integer getPlatformID() {
            return platformID;
        }
        
        public void setPlatformID(Integer platformID) {
            this.platformID = platformID;
        }
        
        public String getDeviceID() {
            return deviceID;
        }
        
        public void setDeviceID(String deviceID) {
            this.deviceID = deviceID;
        }
        
        public Long getLastActiveTime() {
            return lastActiveTime;
        }
        
        public void setLastActiveTime(Long lastActiveTime) {
            this.lastActiveTime = lastActiveTime;
        }
    }

    class PushTemplate {
        private Integer messageType;
        private Integer sessionType;

        public PushTemplate() {
        }

        public PushTemplate(Integer messageType, Integer sessionType) {
            this.messageType = messageType;
            this.sessionType = sessionType;
        }

        public Integer getMessageType() {
            return messageType;
        }

        public void setMessageType(Integer messageType) {
            this.messageType = messageType;
        }

        public Integer getSessionType() {
            return sessionType;
        }

        public void setSessionType(Integer sessionType) {
            this.sessionType = sessionType;
        }
    }

    class UserPushSettings {
        private String userID;
        private boolean pushEnabled;

        public UserPushSettings() {
        }

        public UserPushSettings(String userID, boolean pushEnabled) {
            this.userID = userID;
            this.pushEnabled = pushEnabled;
        }

        public String getUserID() {
            return userID;
        }

        public void setUserID(String userID) {
            this.userID = userID;
        }

        public boolean isPushEnabled() {
            return pushEnabled;
        }

        public void setPushEnabled(boolean pushEnabled) {
            this.pushEnabled = pushEnabled;
        }
    }
}
