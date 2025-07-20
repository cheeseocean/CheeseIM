package com.cheeseocean.im.push.service;

import java.util.List;
import java.util.Map;

/**
 * 设备Token管理服务接口
 * 负责设备Token的存储、查询、更新和删除
 * 
 * @author CheeseIM
 */
public interface DeviceTokenService {
    
    /**
     * 保存设备Token
     * 
     * @param userID 用户ID
     * @param platformID 平台ID (1:iOS, 2:Android, 3:Web, 4:Windows, 5:Mac)
     * @param deviceToken 设备Token
     * @return 是否保存成功
     */
    boolean saveDeviceToken(String userID, Integer platformID, String deviceToken);
    
    /**
     * 获取设备Token
     * 
     * @param userID 用户ID
     * @param platformID 平台ID
     * @return 设备Token
     */
    String getDeviceToken(String userID, Integer platformID);
    
    /**
     * 获取用户所有平台的设备Token
     * 
     * @param userID 用户ID
     * @return 平台ID -> 设备Token 的映射
     */
    Map<Integer, String> getUserDeviceTokens(String userID);
    
    /**
     * 批量获取设备Token
     * 
     * @param userIDs 用户ID列表
     * @param platformID 平台ID
     * @return 用户ID -> 设备Token 的映射
     */
    Map<String, String> batchGetDeviceTokens(List<String> userIDs, Integer platformID);
    
    /**
     * 检查设备Token是否存在
     * 
     * @param userID 用户ID
     * @param platformID 平台ID
     * @return 是否存在
     */
    boolean hasDeviceToken(String userID, Integer platformID);
    
    /**
     * 删除设备Token
     * 
     * @param userID 用户ID
     * @param platformID 平台ID
     * @return 是否删除成功
     */
    boolean removeDeviceToken(String userID, Integer platformID);
    
    /**
     * 删除用户所有设备Token
     * 
     * @param userID 用户ID
     * @return 删除的Token数量
     */
    int removeAllUserDeviceTokens(String userID);
    
    /**
     * 更新设备Token的最后活跃时间
     * 
     * @param userID 用户ID
     * @param platformID 平台ID
     * @return 是否更新成功
     */
    boolean updateTokenActiveTime(String userID, Integer platformID);
    
    /**
     * 清理过期的设备Token
     * 
     * @return 清理的Token数量
     */
    int cleanupExpiredTokens();
    
    /**
     * 获取设备Token统计信息
     * 
     * @return 统计信息
     */
    DeviceTokenStats getDeviceTokenStats();
    
    /**
     * 设备Token统计信息类
     */
    class DeviceTokenStats {
        private long totalTokens;
        private long activeTokens;
        private long expiredTokens;
        private Map<Integer, Long> platformDistribution;
        private long lastUpdateTime;
        
        public DeviceTokenStats() {
            this.lastUpdateTime = System.currentTimeMillis();
        }
        
        // Getter and Setter
        public long getTotalTokens() {
            return totalTokens;
        }
        
        public void setTotalTokens(long totalTokens) {
            this.totalTokens = totalTokens;
        }
        
        public long getActiveTokens() {
            return activeTokens;
        }
        
        public void setActiveTokens(long activeTokens) {
            this.activeTokens = activeTokens;
        }
        
        public long getExpiredTokens() {
            return expiredTokens;
        }
        
        public void setExpiredTokens(long expiredTokens) {
            this.expiredTokens = expiredTokens;
        }
        
        public Map<Integer, Long> getPlatformDistribution() {
            return platformDistribution;
        }
        
        public void setPlatformDistribution(Map<Integer, Long> platformDistribution) {
            this.platformDistribution = platformDistribution;
        }
        
        public long getLastUpdateTime() {
            return lastUpdateTime;
        }
        
        public void setLastUpdateTime(long lastUpdateTime) {
            this.lastUpdateTime = lastUpdateTime;
        }
        
        @Override
        public String toString() {
            return "DeviceTokenStats{" +
                    "totalTokens=" + totalTokens +
                    ", activeTokens=" + activeTokens +
                    ", expiredTokens=" + expiredTokens +
                    ", platformDistribution=" + platformDistribution +
                    ", lastUpdateTime=" + lastUpdateTime +
                    '}';
        }
    }
}
