package com.cheeseocean.im.push.service.impl;

import com.cheeseocean.im.push.service.PushConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 推送配置服务实现
 * 基于Redis存储用户的推送配置信息
 * 
 * @author CheeseIM
 */
@Service
public class PushConfigServiceImpl implements PushConfigService {
    
    private static final Logger logger = LoggerFactory.getLogger(PushConfigServiceImpl.class);
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Value("${cheese.im.push.config.cache-expire-days:7}")
    private int cacheExpireDays;
    
    @Value("${cheese.im.push.config.default-group-chat-read-type:READ_ALL}")
    private String defaultGroupChatReadType;
    
    /**
     * Redis Key前缀
     */
    private static final String USER_GROUP_CHAT_READ_TYPE_KEY_PREFIX = "cheese_im:push_config:group_chat_read_type:";
    private static final String USER_GLOBAL_GROUP_CHAT_READ_TYPE_KEY_PREFIX = "cheese_im:push_config:global_group_chat_read_type:";
    private static final String USER_PUSH_ENABLED_KEY_PREFIX = "cheese_im:push_config:push_enabled:";
    private static final String USER_OFFLINE_PUSH_ENABLED_KEY_PREFIX = "cheese_im:push_config:offline_push_enabled:";
    private static final String USER_GROUP_PUSH_ENABLED_KEY_PREFIX = "cheese_im:push_config:group_push_enabled:";
    
    @Override
    public GroupChatReadType getUserGroupChatReadType(String userID, String groupID) {
        try {
            if (userID == null || userID.trim().isEmpty()) {
                return getDefaultGroupChatReadType();
            }
            
            // 先查找群组特定的设置
            if (groupID != null && !groupID.trim().isEmpty()) {
                String groupSpecificKey = USER_GROUP_CHAT_READ_TYPE_KEY_PREFIX + userID + ":" + groupID;
                Object value = redisTemplate.opsForValue().get(groupSpecificKey);
                
                if (value != null) {
                    try {
                        int intValue = Integer.parseInt(value.toString());
                        GroupChatReadType type = GroupChatReadType.fromValue(intValue);
                        logger.debug("获取用户群组特定群聊读取类型: userID={}, groupID={}, type={}", 
                                   userID, groupID, type);
                        return type;
                    } catch (NumberFormatException e) {
                        logger.warn("解析群聊读取类型失败: userID={}, groupID={}, value={}", 
                                   userID, groupID, value);
                    }
                }
            }
            
            // 查找用户全局设置
            GroupChatReadType globalType = getUserGlobalGroupChatReadType(userID);
            logger.debug("获取用户群聊读取类型: userID={}, groupID={}, type={}", 
                       userID, groupID, globalType);
            
            return globalType;
            
        } catch (Exception e) {
            logger.error("获取用户群聊读取类型失败: userID={}, groupID={}", userID, groupID, e);
            return getDefaultGroupChatReadType();
        }
    }
    
    @Override
    public boolean setUserGroupChatReadType(String userID, String groupID, GroupChatReadType readType) {
        try {
            if (userID == null || userID.trim().isEmpty() || readType == null) {
                return false;
            }
            
            String key;
            if (groupID != null && !groupID.trim().isEmpty()) {
                // 设置群组特定的配置
                key = USER_GROUP_CHAT_READ_TYPE_KEY_PREFIX + userID + ":" + groupID;
            } else {
                // 设置全局配置
                key = USER_GLOBAL_GROUP_CHAT_READ_TYPE_KEY_PREFIX + userID;
            }
            
            redisTemplate.opsForValue().set(key, readType.getValue(), cacheExpireDays, TimeUnit.DAYS);
            
            logger.info("设置用户群聊读取类型: userID={}, groupID={}, type={}", 
                       userID, groupID, readType);
            
            return true;
            
        } catch (Exception e) {
            logger.error("设置用户群聊读取类型失败: userID={}, groupID={}, type={}", 
                        userID, groupID, readType, e);
            return false;
        }
    }
    
    @Override
    public GroupChatReadType getUserGlobalGroupChatReadType(String userID) {
        try {
            if (userID == null || userID.trim().isEmpty()) {
                return getDefaultGroupChatReadType();
            }
            
            String key = USER_GLOBAL_GROUP_CHAT_READ_TYPE_KEY_PREFIX + userID;
            Object value = redisTemplate.opsForValue().get(key);
            
            if (value != null) {
                try {
                    int intValue = Integer.parseInt(value.toString());
                    return GroupChatReadType.fromValue(intValue);
                } catch (NumberFormatException e) {
                    logger.warn("解析用户全局群聊读取类型失败: userID={}, value={}", userID, value);
                }
            }
            
            // 返回系统默认值
            return getDefaultGroupChatReadType();
            
        } catch (Exception e) {
            logger.error("获取用户全局群聊读取类型失败: userID={}", userID, e);
            return getDefaultGroupChatReadType();
        }
    }
    
    @Override
    public boolean setUserGlobalGroupChatReadType(String userID, GroupChatReadType readType) {
        return setUserGroupChatReadType(userID, null, readType);
    }
    
    @Override
    public boolean isPushEnabled(String userID) {
        try {
            if (userID == null || userID.trim().isEmpty()) {
                return true; // 默认启用
            }
            
            String key = USER_PUSH_ENABLED_KEY_PREFIX + userID;
            Object value = redisTemplate.opsForValue().get(key);
            
            if (value != null) {
                return Boolean.parseBoolean(value.toString());
            }
            
            return true; // 默认启用
            
        } catch (Exception e) {
            logger.error("检查用户推送启用状态失败: userID={}", userID, e);
            return true; // 默认启用
        }
    }
    
    @Override
    public boolean isOfflinePushEnabled(String userID) {
        try {
            if (userID == null || userID.trim().isEmpty()) {
                return true; // 默认启用
            }
            
            String key = USER_OFFLINE_PUSH_ENABLED_KEY_PREFIX + userID;
            Object value = redisTemplate.opsForValue().get(key);
            
            if (value != null) {
                return Boolean.parseBoolean(value.toString());
            }
            
            return true; // 默认启用
            
        } catch (Exception e) {
            logger.error("检查用户离线推送启用状态失败: userID={}", userID, e);
            return true; // 默认启用
        }
    }
    
    @Override
    public boolean isGroupPushEnabled(String userID, String groupID) {
        try {
            if (userID == null || userID.trim().isEmpty() || 
                groupID == null || groupID.trim().isEmpty()) {
                return true; // 默认启用
            }
            
            String key = USER_GROUP_PUSH_ENABLED_KEY_PREFIX + userID + ":" + groupID;
            Object value = redisTemplate.opsForValue().get(key);
            
            if (value != null) {
                return Boolean.parseBoolean(value.toString());
            }
            
            return true; // 默认启用
            
        } catch (Exception e) {
            logger.error("检查用户群组推送启用状态失败: userID={}, groupID={}", userID, groupID, e);
            return true; // 默认启用
        }
    }
    
    @Override
    public GroupChatReadType getDefaultGroupChatReadType() {
        try {
            return GroupChatReadType.valueOf(defaultGroupChatReadType);
        } catch (Exception e) {
            logger.warn("解析默认群聊读取类型失败: {}, 使用READ_ALL", defaultGroupChatReadType);
            return GroupChatReadType.READ_ALL;
        }
    }
    
    /**
     * 设置用户推送启用状态
     */
    public boolean setUserPushEnabled(String userID, boolean enabled) {
        try {
            if (userID == null || userID.trim().isEmpty()) {
                return false;
            }
            
            String key = USER_PUSH_ENABLED_KEY_PREFIX + userID;
            redisTemplate.opsForValue().set(key, enabled, cacheExpireDays, TimeUnit.DAYS);
            
            logger.info("设置用户推送启用状态: userID={}, enabled={}", userID, enabled);
            return true;
            
        } catch (Exception e) {
            logger.error("设置用户推送启用状态失败: userID={}, enabled={}", userID, enabled, e);
            return false;
        }
    }
    
    /**
     * 设置用户离线推送启用状态
     */
    public boolean setUserOfflinePushEnabled(String userID, boolean enabled) {
        try {
            if (userID == null || userID.trim().isEmpty()) {
                return false;
            }
            
            String key = USER_OFFLINE_PUSH_ENABLED_KEY_PREFIX + userID;
            redisTemplate.opsForValue().set(key, enabled, cacheExpireDays, TimeUnit.DAYS);
            
            logger.info("设置用户离线推送启用状态: userID={}, enabled={}", userID, enabled);
            return true;
            
        } catch (Exception e) {
            logger.error("设置用户离线推送启用状态失败: userID={}, enabled={}", userID, enabled, e);
            return false;
        }
    }
    
    /**
     * 设置用户群组推送启用状态
     */
    public boolean setUserGroupPushEnabled(String userID, String groupID, boolean enabled) {
        try {
            if (userID == null || userID.trim().isEmpty() || 
                groupID == null || groupID.trim().isEmpty()) {
                return false;
            }
            
            String key = USER_GROUP_PUSH_ENABLED_KEY_PREFIX + userID + ":" + groupID;
            redisTemplate.opsForValue().set(key, enabled, cacheExpireDays, TimeUnit.DAYS);
            
            logger.info("设置用户群组推送启用状态: userID={}, groupID={}, enabled={}", 
                       userID, groupID, enabled);
            return true;
            
        } catch (Exception e) {
            logger.error("设置用户群组推送启用状态失败: userID={}, groupID={}, enabled={}", 
                        userID, groupID, enabled, e);
            return false;
        }
    }
}
