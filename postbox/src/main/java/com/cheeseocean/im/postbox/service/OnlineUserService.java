package com.cheeseocean.im.postbox.service;

import java.util.List;
import java.util.Set;

/**
 * 在线用户服务接口
 * 参照OpenIM Server的在线用户检测功能
 * 
 * @author CheeseIM
 */
public interface OnlineUserService {
    
    /**
     * 检查用户是否在线
     * 
     * @param userID 用户ID
     * @return 是否在线
     */
    boolean isUserOnline(String userID);
    
    /**
     * 批量检查用户是否在线
     * 
     * @param userIDs 用户ID列表
     * @return 在线的用户ID列表
     */
    List<String> getOnlineUsers(List<String> userIDs);
    
    /**
     * 获取所有在线用户
     * 
     * @return 在线用户ID集合
     */
    Set<String> getAllOnlineUsers();
    
    /**
     * 获取用户的在线平台列表
     * 
     * @param userID 用户ID
     * @return 在线平台ID列表
     */
    List<Integer> getUserOnlinePlatforms(String userID);
    
    /**
     * 检查用户在指定平台是否在线
     * 
     * @param userID 用户ID
     * @param platformID 平台ID
     * @return 是否在线
     */
    boolean isUserOnlineOnPlatform(String userID, Integer platformID);
    
    /**
     * 获取在线用户统计信息
     * 
     * @return 在线用户统计
     */
    OnlineUserStats getOnlineUserStats();
    
    /**
     * 在线用户统计信息
     */
    class OnlineUserStats {
        private long totalOnlineUsers;
        private long totalConnections;
        private java.util.Map<Integer, Long> platformStats;
        private long lastUpdateTime;
        
        public OnlineUserStats() {
            this.lastUpdateTime = System.currentTimeMillis();
        }
        
        // Getter and Setter
        public long getTotalOnlineUsers() {
            return totalOnlineUsers;
        }
        
        public void setTotalOnlineUsers(long totalOnlineUsers) {
            this.totalOnlineUsers = totalOnlineUsers;
        }
        
        public long getTotalConnections() {
            return totalConnections;
        }
        
        public void setTotalConnections(long totalConnections) {
            this.totalConnections = totalConnections;
        }
        
        public java.util.Map<Integer, Long> getPlatformStats() {
            return platformStats;
        }
        
        public void setPlatformStats(java.util.Map<Integer, Long> platformStats) {
            this.platformStats = platformStats;
        }
        
        public long getLastUpdateTime() {
            return lastUpdateTime;
        }
        
        public void setLastUpdateTime(long lastUpdateTime) {
            this.lastUpdateTime = lastUpdateTime;
        }
        
        @Override
        public String toString() {
            return "OnlineUserStats{" +
                    "totalOnlineUsers=" + totalOnlineUsers +
                    ", totalConnections=" + totalConnections +
                    ", platformStats=" + platformStats +
                    ", lastUpdateTime=" + lastUpdateTime +
                    '}';
        }
    }
}
