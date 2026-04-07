package com.cheeseocean.im.postman.service.impl;

import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.enums.PlatformType;
import com.cheeseocean.im.common.core.constants.MessageDisplayConstants;
import com.cheeseocean.im.common.api.enums.ContentType;
import com.cheeseocean.im.common.api.enums.SessionType;
import com.cheeseocean.im.postman.entity.OfflinePushConfig;
import com.cheeseocean.im.postman.entity.OfflinePushResult;
import com.cheeseocean.im.postman.entity.PushMessage;
import com.cheeseocean.im.postman.service.OfflinePushService;
import com.cheeseocean.im.postman.provider.PushProvider;
import com.cheeseocean.im.common.core.logging.CommonLoggers;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 离线推送服务实现
 * 通过第三方推送服务进行离线推送
 * 
 * @author xxxcrel
 */
@Service
public class OfflinePushServiceImpl implements OfflinePushService {
    
    private static final Logger logger = CommonLoggers.POSTMAN;
    
    @Autowired
    private List<PushProvider> pushProviders;
    
    @Autowired
    private DeviceTokenServiceImpl deviceTokenService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 用户离线推送配置缓存Key前缀
     */
    private static final String OFFLINE_PUSH_CONFIG_KEY_PREFIX = "cheese_im:offline_push_config:";
    
    /**
     * 缓存过期时间（小时）
     */
    private static final long CACHE_EXPIRE_HOURS = 24;
    
    @Override
    public OfflinePushResult pushMessageToUsers(Message message, List<String> targetUsers) {
        try {
            if (message == null || targetUsers == null || targetUsers.isEmpty()) {
                return OfflinePushResult.failure("参数无效");
            }
            
            long startTime = System.currentTimeMillis();
            
            logger.info("开始离线推送: messageID={}, targetUsers={}", 
                       message.getServerMsgId(), targetUsers.size());
            
            List<String> successUsers = new ArrayList<>();
            List<String> failedUsers = new ArrayList<>();
            Map<String, String> userErrors = new HashMap<>();
            Map<String, String> providerResults = new HashMap<>();
            
            // 为每个用户创建推送消息并发送
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            
            for (String userID : targetUsers) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        // 检查用户是否启用离线推送
                        if (!isOfflinePushEnabled(userID)) {
                            synchronized (failedUsers) {
                                failedUsers.add(userID);
                                userErrors.put(userID, "用户已禁用离线推送");
                            }
                            return;
                        }
                        
                        // 获取用户离线推送配置
                        OfflinePushConfig config = getUserOfflinePushConfig(userID);
                        
                        // 检查是否在免打扰时间
                        if (config.isInQuietTime() && !config.isAllowDuringQuietTime()) {
                            synchronized (failedUsers) {
                                failedUsers.add(userID);
                                userErrors.put(userID, "当前在免打扰时间");
                            }
                            return;
                        }
                        
                        // 检查是否达到每日推送上限
                        if (config.isReachedDailyLimit()) {
                            synchronized (failedUsers) {
                                failedUsers.add(userID);
                                userErrors.put(userID, "已达到每日推送上限");
                            }
                            return;
                        }
                        
                        // 生成推送内容
                        String title = generatePushTitle(message);
                        String content = generatePushContent(message);
                        
                        // 为用户创建推送消息
                        List<PushMessage> pushMessages = createPushMessagesForUser(
                                userID, title, content, message);
                        
                        if (pushMessages.isEmpty()) {
                            synchronized (failedUsers) {
                                failedUsers.add(userID);
                                userErrors.put(userID, "无可用的推送消息");
                            }
                            return;
                        }
                        
                        // 发送推送消息
                        boolean userPushSuccess = false;
                        StringBuilder userErrorBuilder = new StringBuilder();
                        
                        for (PushMessage pushMessage : pushMessages) {
                            // 选择推送提供商
                            PushProvider provider = selectPushProvider(pushMessage.getPlatformType());
                            if (provider == null) {
                                userErrorBuilder.append("平台").append(pushMessage.getPlatformType().getDisplayName())
                                               .append("无可用推送提供商; ");
                                continue;
                            }
                            
                            // 发送推送
                            PushProvider.PushResult pushResult = provider.sendPush(pushMessage);
                            
                            synchronized (providerResults) {
                                providerResults.put(userID + "_" + provider.getProviderName(), 
                                                  pushResult.isSuccess() ? "成功" : pushResult.getErrorMessage());
                            }
                            
                            if (pushResult.isSuccess()) {
                                userPushSuccess = true;
                                break; // 任意一个平台推送成功即可
                            } else {
                                userErrorBuilder.append(provider.getProviderName())
                                               .append(": ").append(pushResult.getErrorMessage()).append("; ");
                            }
                        }
                        
                        if (userPushSuccess) {
                            synchronized (successUsers) {
                                successUsers.add(userID);
                            }
                            
                            // 更新用户每日推送计数
                            config.setCurrentDailyCount(config.getCurrentDailyCount() + 1);
                            updateUserOfflinePushConfig(userID, config);
                            
                        } else {
                            synchronized (failedUsers) {
                                failedUsers.add(userID);
                                userErrors.put(userID, userErrorBuilder.toString());
                            }
                        }
                        
                    } catch (Exception e) {
                        logger.error("用户离线推送异常: userID={}", userID, e);
                        synchronized (failedUsers) {
                            failedUsers.add(userID);
                            userErrors.put(userID, "推送异常: " + e.getMessage());
                        }
                    }
                });
                
                futures.add(future);
            }
            
            // 等待所有推送完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            
            long totalResponseTime = System.currentTimeMillis() - startTime;
            
            // 构造结果
            OfflinePushResult result;
            if (successUsers.isEmpty() && failedUsers.isEmpty()) {
                result = OfflinePushResult.failure("没有用户进行推送");
            } else if (failedUsers.isEmpty()) {
                result = OfflinePushResult.success(successUsers);
            } else {
                result = OfflinePushResult.partial(successUsers, failedUsers, userErrors);
            }
            
            result.setProviderResults(providerResults);
            result.setTotalResponseTime(totalResponseTime);
            
            logger.info("离线推送完成: messageID={}, targetUsers={}, successCount={}, failedCount={}, totalTime={}ms", 
                       message.getServerMsgId(), targetUsers.size(),
                       successUsers.size(), failedUsers.size(), totalResponseTime);
            
            return result;
            
        } catch (Exception e) {
            logger.error("离线推送异常: messageID={}, targetUsers={}", 
                        message.getServerMsgId(), targetUsers.size(), e);
            return OfflinePushResult.failure("离线推送异常: " + e.getMessage());
        }
    }
    
    @Override
    public OfflinePushResult pushMessageToUser(Message message, String userID) {
        List<String> targetUsers = new ArrayList<>();
        targetUsers.add(userID);
        return pushMessageToUsers(message, targetUsers);
    }
    
    @Override
    public boolean isOfflinePushEnabled(String userID) {
        try {
            OfflinePushConfig config = getUserOfflinePushConfig(userID);
            return config.isEnabled();
        } catch (Exception e) {
            logger.error("检查用户离线推送状态失败: userID={}", userID, e);
            return true; // 默认启用
        }
    }
    
    @Override
    public OfflinePushConfig getUserOfflinePushConfig(String userID) {
        try {
            String configKey = OFFLINE_PUSH_CONFIG_KEY_PREFIX + userID;
            
            // 从缓存获取配置
            Map<Object, Object> configMap = redisTemplate.opsForHash().entries(configKey);
            
            if (configMap != null && !configMap.isEmpty()) {
                return mapToOfflinePushConfig(configMap);
            }
            
            // 缓存未命中，创建默认配置
            OfflinePushConfig config = new OfflinePushConfig(userID);
            
            // 缓存配置
            Map<String, Object> configData = offlinePushConfigToMap(config);
            redisTemplate.opsForHash().putAll(configKey, configData);
            redisTemplate.expire(configKey, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
            
            return config;
            
        } catch (Exception e) {
            logger.error("获取用户离线推送配置失败: userID={}", userID, e);
            return new OfflinePushConfig(userID);
        }
    }
    
    @Override
    public boolean updateUserOfflinePushConfig(String userID, OfflinePushConfig config) {
        try {
            String configKey = OFFLINE_PUSH_CONFIG_KEY_PREFIX + userID;
            
            config.setLastUpdateTime(System.currentTimeMillis());
            
            Map<String, Object> configData = offlinePushConfigToMap(config);
            redisTemplate.opsForHash().putAll(configKey, configData);
            redisTemplate.expire(configKey, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
            
            logger.debug("用户离线推送配置已更新: userID={}", userID);
            return true;
            
        } catch (Exception e) {
            logger.error("更新用户离线推送配置失败: userID={}", userID, e);
            return false;
        }
    }
    
    /**
     * 选择推送提供商
     */
    private PushProvider selectPushProvider(PlatformType platformType) {
        if (pushProviders == null || pushProviders.isEmpty()) {
            return null;
        }
        
        // 优先选择支持指定平台且可用的提供商
        for (PushProvider provider : pushProviders) {
            if (provider.supportsPlatform(platformType) && provider.isAvailable()) {
                return provider;
            }
        }
        
        return null;
    }
    
    /**
     * 生成推送标题
     */
    private String generatePushTitle(Message message) {
        if (isNotificationMessage(message)) {
            return MessageDisplayConstants.PUSH_TITLE_SYSTEM_NOTIFICATION;
        }
        SessionType sessionType = message.getSessionType();
        if (sessionType == SessionType.SINGLE) {
            // 单聊
            return message.getSenderNickName() != null ? message.getSenderNickName() : MessageDisplayConstants.PUSH_TITLE_NEW_MESSAGE;
        } else if (sessionType == SessionType.GROUP) {
            // 群聊
            return MessageDisplayConstants.PUSH_TITLE_GROUP_MESSAGE;
        } else {
            return MessageDisplayConstants.PUSH_TITLE_SYSTEM_NOTIFICATION;
        }
    }
    
    /**
     * 生成推送内容
     */
    private String generatePushContent(Message message) {
        String content = Arrays.toString(message.getContent());
        if (content == null || content.trim().isEmpty()) {
            if (isNotificationMessage(message)) {
                return MessageDisplayConstants.PUSH_CONTENT_NEW_SYSTEM_NOTIFICATION;
            }
            // 根据消息类型生成默认内容
            ContentType contentType = message.getContentType();
            if (contentType != null) {
                switch (contentType) {
                    case IMAGE: return MessageDisplayConstants.PUSH_CONTENT_IMAGE;
                    case VOICE: return MessageDisplayConstants.PUSH_CONTENT_VOICE;
                    case VIDEO: return MessageDisplayConstants.PUSH_CONTENT_VIDEO;
                    case FILE: return MessageDisplayConstants.PUSH_CONTENT_FILE;
                    case LOCATION: return MessageDisplayConstants.PUSH_CONTENT_LOCATION;
                    default: return MessageDisplayConstants.PUSH_TITLE_NEW_MESSAGE;
                }
            }
            return MessageDisplayConstants.PUSH_TITLE_NEW_MESSAGE;
        }
        
        // 限制内容长度
        if (content.length() > 100) {
            return content.substring(0, 97) + "...";
        }
        
        return content;
    }

    private boolean isNotificationMessage(Message message) {
        if (message == null) {
            return false;
        }
        if (message.getSessionType() == SessionType.NOTIFICATION) {
            return true;
        }
        return message.getOptions() != null && message.getOptions().getNotification();
    }

    private List<PushMessage> createPushMessagesForUser(String userID, String title, String content, Message originalMessage) {
        List<PushMessage> pushMessages = new ArrayList<>();
        Map<Integer, String> tokens = deviceTokenService.getUserDeviceTokens(userID);
        tokens.forEach((platformId, token) -> {
            PushMessage pushMessage = createPushMessageForPlatform(userID, platformId, title, content, originalMessage);
            if (pushMessage != null) {
                pushMessage.setDeviceToken(token);
                pushMessages.add(pushMessage);
            }
        });
        return pushMessages;
    }

    private PushMessage createPushMessageForPlatform(String userID, Integer platformID, String title, String content, Message originalMessage) {
        try {
            PushMessage pushMessage = new PushMessage(userID, title, content);
            pushMessage.setPlatformID(platformID);

            if (originalMessage != null) {
                SessionType sessionType = originalMessage.getSessionType();
                pushMessage.setMessageID(originalMessage.getServerMsgId());
                pushMessage.setSenderID(originalMessage.getSenderId());
                pushMessage.setSenderNickname(originalMessage.getSenderNickName());
                pushMessage.setMessageType(originalMessage.getContentType().getCode());
                pushMessage.setConversationType(sessionType.getCode());

                if (sessionType == SessionType.SINGLE) {
                    pushMessage.setConversationID("single_" + originalMessage.getSenderId() + "_" + originalMessage.getReceiverId());
                } else if (sessionType == SessionType.GROUP) {
                    pushMessage.setConversationID("group_" + originalMessage.getGroupId());
                }

                Map<String, Object> extraData = new HashMap<>();
                if (originalMessage.getUniqueId() != null) {
                    extraData.put("uniqueID", originalMessage.getUniqueId());
                }
                pushMessage.setExtras(extraData);
            }

            configurePlatformSpecificProperties(pushMessage, platformID);
            return pushMessage;
        } catch (Exception e) {
            logger.error("创建平台推送消息失败: userID={}, platformID={}", userID, platformID, e);
            return null;
        }
    }

    private SessionType resolveSessionType(Integer sessionType) {
        if (sessionType == null) {
            return SessionType.SINGLE;
        }
        try {
            return SessionType.fromCode(sessionType);
        } catch (IllegalArgumentException ex) {
            return SessionType.SINGLE;
        }
    }

    private ContentType resolveContentType(Integer contentType) {
        if (contentType == null) {
            return null;
        }
        try {
            return ContentType.fromCode(contentType);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void configurePlatformSpecificProperties(PushMessage pushMessage, Integer platformID) {
        switch (PlatformType.fromCode(platformID)) {
            case IOS:
                pushMessage.setSound("default");
                pushMessage.setBadge(1);
                pushMessage.setCategory("MESSAGE");
                break;
            case ANDROID:
                pushMessage.setSound("default");
                pushMessage.setPriority(2);
                break;
            default:
                pushMessage.setPriority(2);
                break;
        }
    }
    
    /**
     * Map转OfflinePushConfig
     */
    private OfflinePushConfig mapToOfflinePushConfig(Map<Object, Object> map) {
        OfflinePushConfig config = new OfflinePushConfig();
        config.setUserID((String) map.get("userID"));
        
        Object enabled = map.get("enabled");
        if (enabled != null) {
            config.setEnabled(Boolean.parseBoolean(enabled.toString()));
        }
        
        Object maxDailyCount = map.get("maxDailyCount");
        if (maxDailyCount != null) {
            config.setMaxDailyCount(Integer.parseInt(maxDailyCount.toString()));
        }
        
        Object currentDailyCount = map.get("currentDailyCount");
        if (currentDailyCount != null) {
            config.setCurrentDailyCount(Integer.parseInt(currentDailyCount.toString()));
        }
        
        config.setQuietStartTime((String) map.get("quietStartTime"));
        config.setQuietEndTime((String) map.get("quietEndTime"));
        
        Object allowDuringQuietTime = map.get("allowDuringQuietTime");
        if (allowDuringQuietTime != null) {
            config.setAllowDuringQuietTime(Boolean.parseBoolean(allowDuringQuietTime.toString()));
        }
        
        Object lastUpdateTime = map.get("lastUpdateTime");
        if (lastUpdateTime != null) {
            config.setLastUpdateTime(Long.parseLong(lastUpdateTime.toString()));
        }
        
        return config;
    }
    
    /**
     * OfflinePushConfig转Map
     */
    private Map<String, Object> offlinePushConfigToMap(OfflinePushConfig config) {
        Map<String, Object> map = new HashMap<>();
        map.put("userID", config.getUserID());
        map.put("enabled", config.isEnabled());
        map.put("maxDailyCount", config.getMaxDailyCount());
        map.put("currentDailyCount", config.getCurrentDailyCount());
        map.put("quietStartTime", config.getQuietStartTime());
        map.put("quietEndTime", config.getQuietEndTime());
        map.put("allowDuringQuietTime", config.isAllowDuringQuietTime());
        map.put("lastUpdateTime", config.getLastUpdateTime());
        return map;
    }
}
