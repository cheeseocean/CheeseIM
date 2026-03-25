package com.cheeseocean.im.postman.entity;

import com.cheeseocean.im.common.core.enums.PlatformType;

import java.io.Serializable;
import java.util.Map;

/**
 * 推送消息类
 * 
 * @author CheeseIM
 */
public class PushMessage implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 消息ID
     */
    private String messageID;
    
    /**
     * 用户ID
     */
    private String userID;
    
    /**
     * 设备Token
     */
    private String deviceToken;
    
    /**
     * 平台ID（1:iOS, 2:Android, 3:Web, 4:Windows, 5:Mac, 6:Linux）
     */
    private Integer platformID;
    
    /**
     * 推送标题
     */
    private String title;
    
    /**
     * 推送内容
     */
    private String content;
    
    /**
     * 发送者ID
     */
    private String senderID;
    
    /**
     * 发送者昵称
     */
    private String senderNickname;
    
    /**
     * 会话ID
     */
    private String conversationID;
    
    /**
     * 会话类型（1:单聊, 2:群聊）
     */
    private Integer conversationType;
    
    /**
     * 消息类型（101:文本, 102:图片, 103:语音, 104:视频, 105:文件, 106:位置等）
     */
    private Integer messageType;

    /**
     * 推送类型，历史代码里与messageType混用。
     */
    private Integer pushType;
    
    /**
     * 推送优先级（1:低, 2:正常, 3:高）
     */
    private Integer priority;
    
    /**
     * 推送声音
     */
    private String sound;
    
    /**
     * 角标数量
     */
    private Integer badge;
    
    /**
     * 推送类别
     */
    private String category;
    
    /**
     * 推送过期时间（毫秒时间戳）
     */
    private Long expireTime;

    /**
     * 是否生产环境（主要用于iOS推送）。
     */
    private Boolean production;
    
    /**
     * 扩展数据
     */
    private Map<String, Object> extras;
    
    /**
     * 创建时间
     */
    private Long createTime;
    
    public PushMessage() {
        this.priority = 2; // 默认正常优先级
        this.sound = "default";
        this.badge = 1;
        this.createTime = System.currentTimeMillis();
    }
    
    public PushMessage(String userID, String title, String content) {
        this();
        this.userID = userID;
        this.title = title;
        this.content = content;
    }
    
    // Getter and Setter methods
    public String getMessageID() {
        return messageID;
    }
    
    public void setMessageID(String messageID) {
        this.messageID = messageID;
    }
    
    public String getUserID() {
        return userID;
    }
    
    public void setUserID(String userID) {
        this.userID = userID;
    }
    
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

    public PlatformType getPlatformType() {
        return PlatformType.fromCode(platformID);
    }

    public void setPlatformType(PlatformType platformType) {
        this.platformID = platformType == null ? null : platformType.getCode();
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public String getSenderID() {
        return senderID;
    }
    
    public void setSenderID(String senderID) {
        this.senderID = senderID;
    }
    
    public String getSenderNickname() {
        return senderNickname;
    }
    
    public void setSenderNickname(String senderNickname) {
        this.senderNickname = senderNickname;
    }
    
    public String getConversationID() {
        return conversationID;
    }
    
    public void setConversationID(String conversationID) {
        this.conversationID = conversationID;
    }
    
    public Integer getConversationType() {
        return conversationType;
    }
    
    public void setConversationType(Integer conversationType) {
        this.conversationType = conversationType;
    }
    
    public Integer getMessageType() {
        return messageType;
    }

    public void setMessageType(Integer messageType) {
        this.messageType = messageType;
    }

    public Integer getPushType() {
        return pushType != null ? pushType : messageType;
    }

    public void setPushType(Integer pushType) {
        this.pushType = pushType;
        if (this.messageType == null) {
            this.messageType = pushType;
        }
    }
    
    public Integer getPriority() {
        return priority;
    }
    
    public void setPriority(Integer priority) {
        this.priority = priority;
    }
    
    public String getSound() {
        return sound;
    }
    
    public void setSound(String sound) {
        this.sound = sound;
    }
    
    public Integer getBadge() {
        return badge;
    }
    
    public void setBadge(Integer badge) {
        this.badge = badge;
    }
    
    public String getCategory() {
        return category;
    }
    
    public void setCategory(String category) {
        this.category = category;
    }
    
    public Long getExpireTime() {
        return expireTime;
    }
    
    public void setExpireTime(Long expireTime) {
        this.expireTime = expireTime;
    }

    public Boolean getProduction() {
        return production;
    }

    public void setProduction(Boolean production) {
        this.production = production;
    }
    
    public Map<String, Object> getExtras() {
        return extras;
    }
    
    public void setExtras(Map<String, Object> extras) {
        this.extras = extras;
    }
    
    public Long getCreateTime() {
        return createTime;
    }
    
    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }

    public static PushMessage createTextPush(String userID, Integer platformID, String senderNickname, String content) {
        PushMessage pushMessage = new PushMessage(userID, senderNickname, content);
        pushMessage.setPlatformID(platformID);
        pushMessage.setPushType(1);
        pushMessage.setPriority(1);
        return pushMessage;
    }

    public static PushMessage createSystemPush(String userID, Integer platformID, String title, String content) {
        PushMessage pushMessage = new PushMessage(userID, title, content);
        pushMessage.setPlatformID(platformID);
        pushMessage.setPushType(7);
        pushMessage.setPriority(2);
        return pushMessage;
    }
    
    @Override
    public String toString() {
        return "PushMessage{" +
                "messageID='" + messageID + '\'' +
                ", userID='" + userID + '\'' +
                ", platformID=" + platformID +
                ", title='" + title + '\'' +
                ", content='" + content + '\'' +
                ", senderID='" + senderID + '\'' +
                ", conversationID='" + conversationID + '\'' +
                ", conversationType=" + conversationType +
                ", messageType=" + messageType +
                ", priority=" + priority +
                '}';
    }
}
