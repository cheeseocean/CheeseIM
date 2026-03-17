package com.cheeseocean.im.push.service.impl;

import com.cheeseocean.im.common.entity.Message;
import com.cheeseocean.im.push.entity.PushMessage;
import com.cheeseocean.im.push.service.DeviceTokenService;
import com.cheeseocean.im.push.service.PushTemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 推送模板服务实现
 * 
 * @author CheeseIM
 */
@Service
public class PushTemplateServiceImpl implements PushTemplateService {
    
    private static final Logger logger = LoggerFactory.getLogger(PushTemplateServiceImpl.class);
    
    @Autowired
    private DeviceTokenService deviceTokenService;
    
    @Override
    public List<PushMessage> createPushMessagesForUser(String userID, String title, String content, Message originalMessage) {
        List<PushMessage> pushMessages = new ArrayList<>();
        
        try {
            // 获取用户的设备Token列表
            List<DeviceTokenInfo> deviceTokens = getUserDeviceTokens(userID);
            
            if (deviceTokens.isEmpty()) {
                logger.warn("用户没有可用的设备Token: userID={}", userID);
                return pushMessages;
            }
            
            // 为每个设备创建推送消息
            for (DeviceTokenInfo tokenInfo : deviceTokens) {
                PushMessage pushMessage = createPushMessageForPlatform(
                    userID, tokenInfo.getPlatformID(), title, content, originalMessage);
                
                if (pushMessage != null) {
                    pushMessage.setDeviceToken(tokenInfo.getDeviceToken());
                    pushMessages.add(pushMessage);
                }
            }
            
        } catch (Exception e) {
            logger.error("创建用户推送消息失败: userID={}", userID, e);
        }
        
        return pushMessages;
    }
    
    @Override
    public PushMessage createPushMessageForPlatform(String userID, Integer platformID, String title, String content, Message originalMessage) {
        try {
            PushMessage pushMessage = new PushMessage(userID, title, content);
            pushMessage.setPlatformID(platformID);
            
            if (originalMessage != null) {
                // 设置消息相关信息
                pushMessage.setMessageID(originalMessage.getServerMsgID());
                pushMessage.setSenderID(originalMessage.getSendID());
                pushMessage.setSenderNickname(originalMessage.getSenderNickname());
                pushMessage.setMessageType(originalMessage.getContentType());
                
                // 设置会话信息
                pushMessage.setConversationType(originalMessage.getSessionType());
                if (originalMessage.getSessionType() == 1) {
                    // 单聊
                    pushMessage.setConversationID("single_" + originalMessage.getSendID() + "_" + originalMessage.getRecvID());
                } else if (originalMessage.getSessionType() == 2) {
                    // 群聊
                    pushMessage.setConversationID("group_" + originalMessage.getGroupID());
                }
                
                // 设置扩展数据
                Map<String, Object> extraData = new HashMap<>();
                if (originalMessage.getAttachedInfo() != null) {
                    extraData.put("attachedInfo", originalMessage.getAttachedInfo());
                }
                if (originalMessage.getEx() != null) {
                    extraData.put("ex", originalMessage.getEx());
                }
                if (originalMessage.getUniqueID() != null) {
                    extraData.put("uniqueID", originalMessage.getUniqueID());
                }
                pushMessage.setExtras(extraData);
            }
            
            // 根据平台设置特定属性
            configurePlatformSpecificProperties(pushMessage, platformID);
            
            return pushMessage;
            
        } catch (Exception e) {
            logger.error("创建平台推送消息失败: userID={}, platformID={}", userID, platformID, e);
            return null;
        }
    }
    
    @Override
    public String generatePushTitle(Message message) {
        if (message == null) {
            return "新消息";
        }
        
        if (message.getSessionType() == 1) {
            // 单聊
            return message.getSenderNickname() != null ? message.getSenderNickname() : "新消息";
        } else if (message.getSessionType() == 2) {
            // 群聊
            return "群聊消息"; // 可以根据需要获取群名称
        } else {
            return "系统通知";
        }
    }
    
    @Override
    public String generatePushContent(Message message) {
        if (message == null) {
            return "新消息";
        }
        
        String content = message.getContent();
        if (content == null || content.trim().isEmpty()) {
            // 根据消息类型生成默认内容
            Integer contentType = message.getContentType();
            if (contentType != null) {
                switch (contentType) {
                    case 102: return "[图片]";
                    case 103: return "[语音]";
                    case 104: return "[视频]";
                    case 105: return "[文件]";
                    case 106: return "[位置]";
                    default: return "新消息";
                }
            }
            return "新消息";
        }
        
        // 限制内容长度
        if (content.length() > 100) {
            return content.substring(0, 97) + "...";
        }
        
        return content;
    }
    
    @Override
    public List<DeviceTokenInfo> getUserDeviceTokens(String userID) {
        List<DeviceTokenInfo> deviceTokens = new ArrayList<>();
        
        try {
            Map<Integer, String> tokens = deviceTokenService.getUserDeviceTokens(userID);
            tokens.forEach((platformId, token) -> {
                DeviceTokenInfo tokenInfo = new DeviceTokenInfo(token, platformId);
                tokenInfo.setLastActiveTime(System.currentTimeMillis());
                deviceTokens.add(tokenInfo);
            });

        } catch (Exception e) {
            logger.error("获取用户设备Token失败: userID={}", userID, e);
        }
        
        return deviceTokens;
    }
    
    /**
     * 根据平台配置特定属性
     */
    private void configurePlatformSpecificProperties(PushMessage pushMessage, Integer platformID) {
        if (platformID == null) {
            return;
        }
        
        switch (platformID) {
            case 1: // iOS
                pushMessage.setSound("default");
                pushMessage.setBadge(1);
                pushMessage.setCategory("MESSAGE");
                break;
            case 2: // Android
                pushMessage.setSound("default");
                pushMessage.setPriority(2);
                break;
            case 3: // Web
                pushMessage.setPriority(2);
                break;
            default:
                pushMessage.setPriority(2);
                break;
        }
    }
}
