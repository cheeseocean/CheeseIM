package com.cheeseocean.im.push.util;

import com.cheeseocean.im.common.entity.Message;

import java.util.HashMap;
import java.util.Map;

/**
 * 推送消息构建器工具类
 * 提供便捷的推送消息构建方法
 * 
 * @author CheeseIM
 */
public class PushMessageBuilder {
    
    private PushMessage pushMessage;
    
    private PushMessageBuilder() {
        this.pushMessage = new PushMessage();
    }
    
    /**
     * 创建推送消息构建器
     */
    public static PushMessageBuilder create() {
        return new PushMessageBuilder();
    }
    
    /**
     * 创建推送消息构建器（基于原始消息）
     */
    public static PushMessageBuilder create(Message originalMessage) {
        PushMessageBuilder builder = new PushMessageBuilder();
        if (originalMessage != null) {
            builder.pushMessage.setMessageID(originalMessage.getServerMsgID());
            builder.pushMessage.setSenderID(originalMessage.getSendID());
            builder.pushMessage.setSenderNickname(originalMessage.getSenderNickname());

            // 设置会话信息
            if (originalMessage.getSessionType() == 1) {
                // 单聊
                builder.pushMessage.setConversationID("single_" + originalMessage.getSendID() + "_" + originalMessage.getRecvID());
            } else if (originalMessage.getSessionType() == 2) {
                // 群聊
                builder.pushMessage.setConversationID("group_" + originalMessage.getGroupID());
            }

            // 根据消息类型设置推送类型
            builder.pushMessage.setPushType(originalMessage.getContentType());

            // 从离线推送信息中获取推送配置
            if (originalMessage.getOfflinePushInfo() != null) {
                var offlinePushInfo = originalMessage.getOfflinePushInfo();
                if (offlinePushInfo.getTitle() != null) {
                    builder.pushMessage.setTitle(offlinePushInfo.getTitle());
                }
                if (offlinePushInfo.getDesc() != null) {
                    builder.pushMessage.setContent(offlinePushInfo.getDesc());
                }
                if (offlinePushInfo.getiOSPushSound() != null) {
                    builder.pushMessage.setSound(offlinePushInfo.getiOSPushSound());
                }
                if (offlinePushInfo.getPushExtras() != null) {
                    builder.pushMessage.setExtras(offlinePushInfo.getPushExtras());
                }
            }

            // 设置平台信息
            if (originalMessage.getSenderPlatformID() != null) {
                builder.pushMessage.setPlatformID(originalMessage.getSenderPlatformID());
            }

            // 设置扩展信息
            if (originalMessage.getAttachedInfo() != null) {
                builder.extra("attachedInfo", originalMessage.getAttachedInfo());
            }
            if (originalMessage.getEx() != null) {
                builder.extra("ex", originalMessage.getEx());
            }
            if (originalMessage.getUniqueID() != null) {
                builder.extra("uniqueID", originalMessage.getUniqueID());
            }
        }
        return builder;
    }
    
    /**
     * 设置用户ID
     */
    public PushMessageBuilder userID(String userID) {
        this.pushMessage.setUserID(userID);
        return this;
    }
    
    /**
     * 设置平台ID
     */
    public PushMessageBuilder platformID(Integer platformID) {
        this.pushMessage.setPlatformID(platformID);
        return this;
    }
    
    /**
     * 设置设备Token
     */
    public PushMessageBuilder deviceToken(String deviceToken) {
        this.pushMessage.setDeviceToken(deviceToken);
        return this;
    }
    
    /**
     * 设置推送标题
     */
    public PushMessageBuilder title(String title) {
        this.pushMessage.setTitle(title);
        return this;
    }
    
    /**
     * 设置推送内容
     */
    public PushMessageBuilder content(String content) {
        this.pushMessage.setContent(content);
        return this;
    }
    
    /**
     * 设置消息ID
     */
    public PushMessageBuilder messageID(String messageID) {
        this.pushMessage.setMessageID(messageID);
        return this;
    }
    
    /**
     * 设置会话ID
     */
    public PushMessageBuilder conversationID(String conversationID) {
        this.pushMessage.setConversationID(conversationID);
        return this;
    }
    
    /**
     * 设置发送者ID
     */
    public PushMessageBuilder senderID(String senderID) {
        this.pushMessage.setSenderID(senderID);
        return this;
    }
    
    /**
     * 设置发送者昵称
     */
    public PushMessageBuilder senderNickname(String senderNickname) {
        this.pushMessage.setSenderNickname(senderNickname);
        return this;
    }
    
    /**
     * 设置推送类型
     */
    public PushMessageBuilder pushType(Integer pushType) {
        this.pushMessage.setPushType(pushType);
        return this;
    }
    
    /**
     * 设置优先级
     */
    public PushMessageBuilder priority(Integer priority) {
        this.pushMessage.setPriority(priority);
        return this;
    }
    
    /**
     * 设置声音
     */
    public PushMessageBuilder sound(String sound) {
        this.pushMessage.setSound(sound);
        return this;
    }
    
    /**
     * 设置角标
     */
    public PushMessageBuilder badge(Integer badge) {
        this.pushMessage.setBadge(badge);
        return this;
    }
    
    /**
     * 设置分类（iOS）
     */
    public PushMessageBuilder category(String category) {
        this.pushMessage.setCategory(category);
        return this;
    }
    
    /**
     * 设置是否生产环境
     */
    public PushMessageBuilder production(Boolean production) {
        this.pushMessage.setProduction(production);
        return this;
    }
    
    /**
     * 设置过期时间
     */
    public PushMessageBuilder expireTime(Long expireTime) {
        this.pushMessage.setExpireTime(expireTime);
        return this;
    }
    
    /**
     * 设置过期时间（相对时间，秒）
     */
    public PushMessageBuilder expireAfter(long seconds) {
        this.pushMessage.setExpireTime(System.currentTimeMillis() + seconds * 1000);
        return this;
    }
    
    /**
     * 添加扩展数据
     */
    public PushMessageBuilder extra(String key, Object value) {
        if (this.pushMessage.getExtras() == null) {
            this.pushMessage.setExtras(new HashMap<>());
        }
        this.pushMessage.getExtras().put(key, value);
        return this;
    }
    
    /**
     * 添加多个扩展数据
     */
    public PushMessageBuilder extras(Map<String, Object> extras) {
        if (extras != null && !extras.isEmpty()) {
            if (this.pushMessage.getExtras() == null) {
                this.pushMessage.setExtras(new HashMap<>());
            }
            this.pushMessage.getExtras().putAll(extras);
        }
        return this;
    }
    
    /**
     * 设置为文本消息推送
     */
    public PushMessageBuilder asTextMessage() {
        this.pushMessage.setPushType(1);
        this.pushMessage.setPriority(1);
        return this;
    }
    
    /**
     * 设置为图片消息推送
     */
    public PushMessageBuilder asImageMessage() {
        this.pushMessage.setPushType(2);
        this.pushMessage.setPriority(1);
        this.pushMessage.setContent("[图片]");
        return this;
    }
    
    /**
     * 设置为语音消息推送
     */
    public PushMessageBuilder asVoiceMessage() {
        this.pushMessage.setPushType(3);
        this.pushMessage.setPriority(1);
        this.pushMessage.setContent("[语音]");
        return this;
    }
    
    /**
     * 设置为视频消息推送
     */
    public PushMessageBuilder asVideoMessage() {
        this.pushMessage.setPushType(4);
        this.pushMessage.setPriority(1);
        this.pushMessage.setContent("[视频]");
        return this;
    }
    
    /**
     * 设置为文件消息推送
     */
    public PushMessageBuilder asFileMessage() {
        this.pushMessage.setPushType(5);
        this.pushMessage.setPriority(1);
        this.pushMessage.setContent("[文件]");
        return this;
    }
    
    /**
     * 设置为位置消息推送
     */
    public PushMessageBuilder asLocationMessage() {
        this.pushMessage.setPushType(6);
        this.pushMessage.setPriority(1);
        this.pushMessage.setContent("[位置]");
        return this;
    }
    
    /**
     * 设置为系统通知推送
     */
    public PushMessageBuilder asSystemNotification() {
        this.pushMessage.setPushType(7);
        this.pushMessage.setPriority(2); // 高优先级
        return this;
    }
    
    /**
     * 设置为iOS推送
     */
    public PushMessageBuilder foriOS() {
        this.pushMessage.setPlatformID(1);
        this.pushMessage.setProduction(true);
        this.pushMessage.setSound("default");
        return this;
    }
    
    /**
     * 设置为Android推送
     */
    public PushMessageBuilder forAndroid() {
        this.pushMessage.setPlatformID(2);
        return this;
    }
    
    /**
     * 设置为Web推送
     */
    public PushMessageBuilder forWeb() {
        this.pushMessage.setPlatformID(3);
        return this;
    }
    
    /**
     * 构建推送消息
     */
    public PushMessage build() {
        // 设置默认值
        if (this.pushMessage.getPriority() == null) {
            this.pushMessage.setPriority(1); // 默认正常优先级
        }
        
        if (this.pushMessage.getPushType() == null) {
            this.pushMessage.setPushType(1); // 默认文本消息
        }
        
        // 设置默认过期时间（24小时）
        if (this.pushMessage.getExpireTime() == null) {
            this.pushMessage.setExpireTime(System.currentTimeMillis() + 24 * 60 * 60 * 1000);
        }
        
        return this.pushMessage;
    }
}
