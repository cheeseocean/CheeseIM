package com.cheeseocean.im.postbox.service.impl;

import com.cheeseocean.im.common.constants.MessageConstants;
import com.cheeseocean.im.postbox.service.OnlineUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 在线用户服务实现
 * 基于Redis实现在线用户状态检测
 * 
 * @author CheeseIM
 */
@Service
public class OnlineUserServiceImpl implements OnlineUserService {
    
    private static final Logger logger = LoggerFactory.getLogger(OnlineUserServiceImpl.class);
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 在线用户缓存过期时间（秒）
     */
    private static final long ONLINE_USER_EXPIRE_SECONDS = 600; // 10分钟
    
    @Override
    public boolean isUserOnline(String userID) {
        try {
            if (userID == null || userID.trim().isEmpty()) {
                return false;
            }
            
            // 检查Redis中是否存在用户在线记录
            String pattern = MessageConstants.REDIS_KEY_USER_ONLINE + userID + ":*";
            Set<String> keys = redisTemplate.keys(pattern);
            
            if (keys == null || keys.isEmpty()) {
                return false;
            }
            
            // 检查是否有有效的在线记录
            for (String key : keys) {
                if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
                    return true;
                }
            }
            
            return false;
            
        } catch (Exception e) {
            logger.error("检查用户在线状态失败: userID={}", userID, e);
            return false;
        }
    }
    
    @Override
    public List<String> getOnlineUsers(List<String> userIDs) {
        if (userIDs == null || userIDs.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<String> onlineUsers = new ArrayList<>();
        
        for (String userID : userIDs) {
            if (isUserOnline(userID)) {
                onlineUsers.add(userID);
            }
        }
        
        return onlineUsers;
    }
    
    @Override
    public Set<String> getAllOnlineUsers() {
        try {
            // 获取所有在线用户的Key
            String pattern = MessageConstants.REDIS_KEY_USER_ONLINE + "*";
            Set<String> keys = redisTemplate.keys(pattern);
            
            if (keys == null || keys.isEmpty()) {
                return new HashSet<>();
            }
            
            // 提取用户ID
            Set<String> onlineUsers = new HashSet<>();
            String prefix = MessageConstants.REDIS_KEY_USER_ONLINE;
            
            for (String key : keys) {
                if (key.startsWith(prefix)) {
                    // 从key中提取userID (格式: cheese_im:user:online:userID:platformID)
                    String suffix = key.substring(prefix.length());
                    int colonIndex = suffix.lastIndexOf(':');
                    if (colonIndex > 0) {
                        String userID = suffix.substring(0, colonIndex);
                        onlineUsers.add(userID);
                    }
                }
            }
            
            return onlineUsers;
            
        } catch (Exception e) {
            logger.error("获取所有在线用户失败", e);
            return new HashSet<>();
        }
    }
    
    @Override
    public List<Integer> getUserOnlinePlatforms(String userID) {
        try {
            if (userID == null || userID.trim().isEmpty()) {
                return new ArrayList<>();
            }
            
            // 获取用户所有平台的在线记录
            String pattern = MessageConstants.REDIS_KEY_USER_ONLINE + userID + ":*";
            Set<String> keys = redisTemplate.keys(pattern);
            
            if (keys == null || keys.isEmpty()) {
                return new ArrayList<>();
            }
            
            List<Integer> onlinePlatforms = new ArrayList<>();
            String prefix = MessageConstants.REDIS_KEY_USER_ONLINE + userID + ":";
            
            for (String key : keys) {
                if (key.startsWith(prefix) && Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
                    // 提取平台ID
                    String platformStr = key.substring(prefix.length());
                    try {
                        Integer platformID = Integer.parseInt(platformStr);
                        onlinePlatforms.add(platformID);
                    } catch (NumberFormatException e) {
                        logger.warn("无效的平台ID格式: {}", platformStr);
                    }
                }
            }
            
            return onlinePlatforms;
            
        } catch (Exception e) {
            logger.error("获取用户在线平台失败: userID={}", userID, e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public boolean isUserOnlineOnPlatform(String userID, Integer platformID) {
        try {
            if (userID == null || userID.trim().isEmpty() || platformID == null) {
                return false;
            }
            
            String key = MessageConstants.REDIS_KEY_USER_ONLINE + userID + ":" + platformID;
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
            
        } catch (Exception e) {
            logger.error("检查用户平台在线状态失败: userID={}, platformID={}", userID, platformID, e);
            return false;
        }
    }
    
    @Override
    public OnlineUserStats getOnlineUserStats() {
        try {
            OnlineUserStats stats = new OnlineUserStats();
            
            // 获取所有在线用户
            Set<String> onlineUsers = getAllOnlineUsers();
            stats.setTotalOnlineUsers(onlineUsers.size());
            
            // 统计各平台连接数
            Map<Integer, Long> platformStats = new HashMap<>();
            long totalConnections = 0;
            
            String pattern = MessageConstants.REDIS_KEY_USER_ONLINE + "*";
            Set<String> keys = redisTemplate.keys(pattern);
            
            if (keys != null) {
                totalConnections = keys.size();
                
                String prefix = MessageConstants.REDIS_KEY_USER_ONLINE;
                for (String key : keys) {
                    if (key.startsWith(prefix)) {
                        String suffix = key.substring(prefix.length());
                        int colonIndex = suffix.lastIndexOf(':');
                        if (colonIndex > 0) {
                            String platformStr = suffix.substring(colonIndex + 1);
                            try {
                                Integer platformID = Integer.parseInt(platformStr);
                                platformStats.merge(platformID, 1L, Long::sum);
                            } catch (NumberFormatException e) {
                                logger.warn("无效的平台ID格式: {}", platformStr);
                            }
                        }
                    }
                }
            }
            
            stats.setTotalConnections(totalConnections);
            stats.setPlatformStats(platformStats);
            
            return stats;
            
        } catch (Exception e) {
            logger.error("获取在线用户统计失败", e);
            
            OnlineUserStats stats = new OnlineUserStats();
            stats.setTotalOnlineUsers(0);
            stats.setTotalConnections(0);
            stats.setPlatformStats(new HashMap<>());
            return stats;
        }
    }
    
    /**
     * 设置用户在线状态（供其他服务调用）
     * 
     * @param userID 用户ID
     * @param platformID 平台ID
     * @param online 是否在线
     */
    public void setUserOnlineStatus(String userID, Integer platformID, boolean online) {
        try {
            if (userID == null || userID.trim().isEmpty() || platformID == null) {
                return;
            }
            
            String key = MessageConstants.REDIS_KEY_USER_ONLINE + userID + ":" + platformID;
            
            if (online) {
                // 设置在线状态，带过期时间
                Map<String, Object> onlineInfo = new HashMap<>();
                onlineInfo.put("userID", userID);
                onlineInfo.put("platformID", platformID);
                onlineInfo.put("onlineTime", System.currentTimeMillis());
                
                redisTemplate.opsForHash().putAll(key, onlineInfo);
                redisTemplate.expire(key, ONLINE_USER_EXPIRE_SECONDS, TimeUnit.SECONDS);
                
                logger.debug("用户上线: userID={}, platformID={}", userID, platformID);
            } else {
                // 删除在线状态
                redisTemplate.delete(key);
                logger.debug("用户下线: userID={}, platformID={}", userID, platformID);
            }
            
        } catch (Exception e) {
            logger.error("设置用户在线状态失败: userID={}, platformID={}, online={}", 
                        userID, platformID, online, e);
        }
    }
    
    /**
     * 更新用户在线状态的过期时间（心跳更新）
     * 
     * @param userID 用户ID
     * @param platformID 平台ID
     */
    public void refreshUserOnlineStatus(String userID, Integer platformID) {
        try {
            if (userID == null || userID.trim().isEmpty() || platformID == null) {
                return;
            }
            
            String key = MessageConstants.REDIS_KEY_USER_ONLINE + userID + ":" + platformID;
            
            if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
                // 更新最后活跃时间
                redisTemplate.opsForHash().put(key, "lastActiveTime", System.currentTimeMillis());
                // 刷新过期时间
                redisTemplate.expire(key, ONLINE_USER_EXPIRE_SECONDS, TimeUnit.SECONDS);
                
                logger.debug("刷新用户在线状态: userID={}, platformID={}", userID, platformID);
            }
            
        } catch (Exception e) {
            logger.error("刷新用户在线状态失败: userID={}, platformID={}", userID, platformID, e);
        }
    }
}
