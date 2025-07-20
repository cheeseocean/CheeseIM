package com.cheeseocean.im.push.service.impl;

import com.cheeseocean.im.push.service.DeviceTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 设备Token管理服务实现
 * 基于Redis实现设备Token的存储、查询、更新和删除
 * 
 * @author CheeseIM
 */
@Service
public class DeviceTokenServiceImpl implements DeviceTokenService {
    
    private static final Logger logger = LoggerFactory.getLogger(DeviceTokenServiceImpl.class);
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Value("${cheese.im.push.device-token.cache-expire-days:30}")
    private int cacheExpireDays;
    
    /**
     * 设备Token缓存Key前缀
     */
    private static final String DEVICE_TOKEN_KEY_PREFIX = "cheese_im:device_token:";
    
    /**
     * 用户设备Token映射Key前缀
     */
    private static final String USER_DEVICE_TOKEN_KEY_PREFIX = "cheese_im:user_device_token:";
    
    /**
     * 设备Token活跃时间Key前缀
     */
    private static final String TOKEN_ACTIVE_TIME_KEY_PREFIX = "cheese_im:token_active_time:";
    
    @Override
    public boolean saveDeviceToken(String userID, Integer platformID, String deviceToken) {
        try {
            if (userID == null || userID.trim().isEmpty() || 
                platformID == null || platformID <= 0 ||
                deviceToken == null || deviceToken.trim().isEmpty()) {
                logger.warn("保存设备Token参数无效: userID={}, platformID={}, deviceToken={}", 
                           userID, platformID, maskToken(deviceToken));
                return false;
            }
            
            // 设备Token详细信息Key
            String tokenKey = DEVICE_TOKEN_KEY_PREFIX + deviceToken;
            
            // 用户设备Token映射Key
            String userTokenKey = USER_DEVICE_TOKEN_KEY_PREFIX + userID;
            
            // 设备Token活跃时间Key
            String activeTimeKey = TOKEN_ACTIVE_TIME_KEY_PREFIX + deviceToken;
            
            long currentTime = System.currentTimeMillis();
            
            // 保存设备Token详细信息
            Map<String, Object> tokenInfo = new HashMap<>();
            tokenInfo.put("userID", userID);
            tokenInfo.put("platformID", platformID);
            tokenInfo.put("deviceToken", deviceToken);
            tokenInfo.put("createTime", currentTime);
            tokenInfo.put("lastActiveTime", currentTime);
            
            redisTemplate.opsForHash().putAll(tokenKey, tokenInfo);
            redisTemplate.expire(tokenKey, cacheExpireDays, TimeUnit.DAYS);
            
            // 保存用户设备Token映射
            redisTemplate.opsForHash().put(userTokenKey, platformID.toString(), deviceToken);
            redisTemplate.expire(userTokenKey, cacheExpireDays, TimeUnit.DAYS);
            
            // 保存设备Token活跃时间
            redisTemplate.opsForValue().set(activeTimeKey, currentTime, cacheExpireDays, TimeUnit.DAYS);
            
            logger.debug("设备Token已保存: userID={}, platformID={}, deviceToken={}", 
                        userID, platformID, maskToken(deviceToken));
            
            return true;
            
        } catch (Exception e) {
            logger.error("保存设备Token失败: userID={}, platformID={}, deviceToken={}", 
                        userID, platformID, maskToken(deviceToken), e);
            return false;
        }
    }
    
    @Override
    public String getDeviceToken(String userID, Integer platformID) {
        try {
            if (userID == null || userID.trim().isEmpty() || platformID == null || platformID <= 0) {
                return null;
            }
            
            String userTokenKey = USER_DEVICE_TOKEN_KEY_PREFIX + userID;
            Object token = redisTemplate.opsForHash().get(userTokenKey, platformID.toString());
            
            String deviceToken = token != null ? token.toString() : null;
            
            // 更新活跃时间
            if (deviceToken != null) {
                updateTokenActiveTime(userID, platformID);
            }
            
            logger.debug("获取设备Token: userID={}, platformID={}, found={}", 
                        userID, platformID, deviceToken != null);
            
            return deviceToken;
            
        } catch (Exception e) {
            logger.error("获取设备Token失败: userID={}, platformID={}", userID, platformID, e);
            return null;
        }
    }
    
    @Override
    public Map<Integer, String> getUserDeviceTokens(String userID) {
        Map<Integer, String> result = new HashMap<>();
        
        try {
            if (userID == null || userID.trim().isEmpty()) {
                return result;
            }
            
            String userTokenKey = USER_DEVICE_TOKEN_KEY_PREFIX + userID;
            Map<Object, Object> tokenMap = redisTemplate.opsForHash().entries(userTokenKey);
            
            if (tokenMap != null && !tokenMap.isEmpty()) {
                for (Map.Entry<Object, Object> entry : tokenMap.entrySet()) {
                    try {
                        Integer platformID = Integer.parseInt(entry.getKey().toString());
                        String deviceToken = entry.getValue().toString();
                        result.put(platformID, deviceToken);
                    } catch (NumberFormatException e) {
                        logger.warn("解析平台ID失败: key={}", entry.getKey());
                    }
                }
            }
            
            logger.debug("获取用户设备Token: userID={}, count={}", userID, result.size());
            
        } catch (Exception e) {
            logger.error("获取用户设备Token失败: userID={}", userID, e);
        }
        
        return result;
    }
    
    @Override
    public Map<String, String> batchGetDeviceTokens(List<String> userIDs, Integer platformID) {
        Map<String, String> result = new HashMap<>();
        
        try {
            if (userIDs == null || userIDs.isEmpty() || platformID == null || platformID <= 0) {
                return result;
            }
            
            for (String userID : userIDs) {
                String deviceToken = getDeviceToken(userID, platformID);
                if (deviceToken != null) {
                    result.put(userID, deviceToken);
                }
            }
            
            logger.debug("批量获取设备Token: userCount={}, platformID={}, foundCount={}", 
                        userIDs.size(), platformID, result.size());
            
        } catch (Exception e) {
            logger.error("批量获取设备Token失败: userCount={}, platformID={}", 
                        userIDs != null ? userIDs.size() : 0, platformID, e);
        }
        
        return result;
    }
    
    @Override
    public boolean hasDeviceToken(String userID, Integer platformID) {
        try {
            String deviceToken = getDeviceToken(userID, platformID);
            return deviceToken != null && !deviceToken.trim().isEmpty();
        } catch (Exception e) {
            logger.error("检查设备Token存在性失败: userID={}, platformID={}", userID, platformID, e);
            return false;
        }
    }
    
    @Override
    public boolean removeDeviceToken(String userID, Integer platformID) {
        try {
            if (userID == null || userID.trim().isEmpty() || platformID == null || platformID <= 0) {
                return false;
            }
            
            // 先获取设备Token
            String deviceToken = getDeviceToken(userID, platformID);
            if (deviceToken == null) {
                return true; // 已经不存在
            }
            
            // 删除设备Token详细信息
            String tokenKey = DEVICE_TOKEN_KEY_PREFIX + deviceToken;
            redisTemplate.delete(tokenKey);
            
            // 删除用户设备Token映射
            String userTokenKey = USER_DEVICE_TOKEN_KEY_PREFIX + userID;
            redisTemplate.opsForHash().delete(userTokenKey, platformID.toString());
            
            // 删除设备Token活跃时间
            String activeTimeKey = TOKEN_ACTIVE_TIME_KEY_PREFIX + deviceToken;
            redisTemplate.delete(activeTimeKey);
            
            logger.debug("设备Token已删除: userID={}, platformID={}, deviceToken={}", 
                        userID, platformID, maskToken(deviceToken));
            
            return true;
            
        } catch (Exception e) {
            logger.error("删除设备Token失败: userID={}, platformID={}", userID, platformID, e);
            return false;
        }
    }
    
    @Override
    public int removeAllUserDeviceTokens(String userID) {
        try {
            if (userID == null || userID.trim().isEmpty()) {
                return 0;
            }
            
            // 获取用户所有设备Token
            Map<Integer, String> userTokens = getUserDeviceTokens(userID);
            
            int removedCount = 0;
            for (Map.Entry<Integer, String> entry : userTokens.entrySet()) {
                if (removeDeviceToken(userID, entry.getKey())) {
                    removedCount++;
                }
            }
            
            logger.info("用户所有设备Token已删除: userID={}, removedCount={}", userID, removedCount);
            
            return removedCount;
            
        } catch (Exception e) {
            logger.error("删除用户所有设备Token失败: userID={}", userID, e);
            return 0;
        }
    }
    
    @Override
    public boolean updateTokenActiveTime(String userID, Integer platformID) {
        try {
            if (userID == null || userID.trim().isEmpty() || platformID == null || platformID <= 0) {
                return false;
            }
            
            String deviceToken = getDeviceToken(userID, platformID);
            if (deviceToken == null) {
                return false;
            }
            
            long currentTime = System.currentTimeMillis();
            
            // 更新设备Token详细信息中的活跃时间
            String tokenKey = DEVICE_TOKEN_KEY_PREFIX + deviceToken;
            redisTemplate.opsForHash().put(tokenKey, "lastActiveTime", currentTime);
            
            // 更新设备Token活跃时间
            String activeTimeKey = TOKEN_ACTIVE_TIME_KEY_PREFIX + deviceToken;
            redisTemplate.opsForValue().set(activeTimeKey, currentTime, cacheExpireDays, TimeUnit.DAYS);
            
            return true;
            
        } catch (Exception e) {
            logger.error("更新设备Token活跃时间失败: userID={}, platformID={}", userID, platformID, e);
            return false;
        }
    }
    
    @Override
    public int cleanupExpiredTokens() {
        try {
            long currentTime = System.currentTimeMillis();
            long expireTime = currentTime - (cacheExpireDays * 24 * 60 * 60 * 1000L);
            
            // 获取所有活跃时间Key
            String pattern = TOKEN_ACTIVE_TIME_KEY_PREFIX + "*";
            Set<String> activeTimeKeys = redisTemplate.keys(pattern);
            
            int cleanedCount = 0;
            
            if (activeTimeKeys != null) {
                for (String activeTimeKey : activeTimeKeys) {
                    try {
                        Object activeTimeObj = redisTemplate.opsForValue().get(activeTimeKey);
                        if (activeTimeObj != null) {
                            long activeTime = Long.parseLong(activeTimeObj.toString());
                            
                            if (activeTime < expireTime) {
                                // 提取设备Token
                                String deviceToken = activeTimeKey.substring(TOKEN_ACTIVE_TIME_KEY_PREFIX.length());
                                
                                // 获取Token详细信息
                                String tokenKey = DEVICE_TOKEN_KEY_PREFIX + deviceToken;
                                Map<Object, Object> tokenInfo = redisTemplate.opsForHash().entries(tokenKey);
                                
                                if (tokenInfo != null && !tokenInfo.isEmpty()) {
                                    String userID = (String) tokenInfo.get("userID");
                                    Object platformIDObj = tokenInfo.get("platformID");
                                    
                                    if (userID != null && platformIDObj != null) {
                                        Integer platformID = Integer.parseInt(platformIDObj.toString());
                                        
                                        // 删除过期Token
                                        if (removeDeviceToken(userID, platformID)) {
                                            cleanedCount++;
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("清理过期Token异常: key={}", activeTimeKey, e);
                    }
                }
            }
            
            logger.info("过期设备Token清理完成: cleanedCount={}", cleanedCount);
            
            return cleanedCount;
            
        } catch (Exception e) {
            logger.error("清理过期设备Token失败", e);
            return 0;
        }
    }
    
    @Override
    public DeviceTokenStats getDeviceTokenStats() {
        DeviceTokenStats stats = new DeviceTokenStats();
        
        try {
            long currentTime = System.currentTimeMillis();
            long expireTime = currentTime - (cacheExpireDays * 24 * 60 * 60 * 1000L);
            
            // 统计所有Token
            String pattern = TOKEN_ACTIVE_TIME_KEY_PREFIX + "*";
            Set<String> activeTimeKeys = redisTemplate.keys(pattern);
            
            long totalTokens = 0;
            long activeTokens = 0;
            long expiredTokens = 0;
            Map<Integer, Long> platformDistribution = new HashMap<>();
            
            if (activeTimeKeys != null) {
                totalTokens = activeTimeKeys.size();
                
                for (String activeTimeKey : activeTimeKeys) {
                    try {
                        Object activeTimeObj = redisTemplate.opsForValue().get(activeTimeKey);
                        if (activeTimeObj != null) {
                            long activeTime = Long.parseLong(activeTimeObj.toString());
                            
                            if (activeTime >= expireTime) {
                                activeTokens++;
                            } else {
                                expiredTokens++;
                            }
                            
                            // 统计平台分布
                            String deviceToken = activeTimeKey.substring(TOKEN_ACTIVE_TIME_KEY_PREFIX.length());
                            String tokenKey = DEVICE_TOKEN_KEY_PREFIX + deviceToken;
                            Object platformIDObj = redisTemplate.opsForHash().get(tokenKey, "platformID");
                            
                            if (platformIDObj != null) {
                                Integer platformID = Integer.parseInt(platformIDObj.toString());
                                platformDistribution.put(platformID, 
                                    platformDistribution.getOrDefault(platformID, 0L) + 1);
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("统计Token异常: key={}", activeTimeKey, e);
                    }
                }
            }
            
            stats.setTotalTokens(totalTokens);
            stats.setActiveTokens(activeTokens);
            stats.setExpiredTokens(expiredTokens);
            stats.setPlatformDistribution(platformDistribution);
            
            logger.debug("设备Token统计: total={}, active={}, expired={}", 
                        totalTokens, activeTokens, expiredTokens);
            
        } catch (Exception e) {
            logger.error("获取设备Token统计失败", e);
        }
        
        return stats;
    }
    
    /**
     * 掩码Token用于日志输出
     */
    private String maskToken(String token) {
        if (token == null || token.length() < 10) {
            return "***";
        }
        return token.substring(0, 6) + "***" + token.substring(token.length() - 4);
    }
}
