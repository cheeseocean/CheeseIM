package com.cheeseocean.im.postman.service;

import java.util.Map;

/**
 * 消息统计服务接口
 * 参照OpenIM Server的消息统计功能
 * 
 * @author CheeseIM
 */
public interface MessageStatisticsService {
    
    /**
     * 记录消息传输统计
     * 
     * @param messageType 消息类型
     * @param sessionType 会话类型
     * @param success 是否成功
     */
    void recordMessageTransfer(String messageType, Integer sessionType, boolean success);
    
    /**
     * 记录消息路由统计
     * 
     * @param routeStrategy 路由策略
     * @param targetUserCount 目标用户数量
     * @param success 是否成功
     */
    void recordMessageRoute(String routeStrategy, int targetUserCount, boolean success);
    
    /**
     * 获取消息传输统计
     * 
     * @return 统计信息
     */
    MessageTransferStats getMessageTransferStats();
    
    /**
     * 获取实时统计信息
     * 
     * @return 实时统计
     */
    RealtimeStats getRealtimeStats();
    
    /**
     * 重置统计信息
     */
    void resetStats();
    
    /**
     * 消息传输统计信息
     */
    class MessageTransferStats {
        private long totalMessages;
        private long successMessages;
        private long failedMessages;
        private Map<String, Long> messageTypeStats;
        private Map<Integer, Long> sessionTypeStats;
        private Map<String, Long> routeStrategyStats;
        private double successRate;
        private long lastUpdateTime;
        
        public MessageTransferStats() {
            this.lastUpdateTime = System.currentTimeMillis();
        }
        
        // Getter and Setter
        public long getTotalMessages() {
            return totalMessages;
        }
        
        public void setTotalMessages(long totalMessages) {
            this.totalMessages = totalMessages;
        }
        
        public long getSuccessMessages() {
            return successMessages;
        }
        
        public void setSuccessMessages(long successMessages) {
            this.successMessages = successMessages;
        }
        
        public long getFailedMessages() {
            return failedMessages;
        }
        
        public void setFailedMessages(long failedMessages) {
            this.failedMessages = failedMessages;
        }
        
        public Map<String, Long> getMessageTypeStats() {
            return messageTypeStats;
        }
        
        public void setMessageTypeStats(Map<String, Long> messageTypeStats) {
            this.messageTypeStats = messageTypeStats;
        }
        
        public Map<Integer, Long> getSessionTypeStats() {
            return sessionTypeStats;
        }
        
        public void setSessionTypeStats(Map<Integer, Long> sessionTypeStats) {
            this.sessionTypeStats = sessionTypeStats;
        }
        
        public Map<String, Long> getRouteStrategyStats() {
            return routeStrategyStats;
        }
        
        public void setRouteStrategyStats(Map<String, Long> routeStrategyStats) {
            this.routeStrategyStats = routeStrategyStats;
        }
        
        public double getSuccessRate() {
            return successRate;
        }
        
        public void setSuccessRate(double successRate) {
            this.successRate = successRate;
        }
        
        public long getLastUpdateTime() {
            return lastUpdateTime;
        }
        
        public void setLastUpdateTime(long lastUpdateTime) {
            this.lastUpdateTime = lastUpdateTime;
        }
        
        @Override
        public String toString() {
            return "MessageTransferStats{" +
                    "totalMessages=" + totalMessages +
                    ", successMessages=" + successMessages +
                    ", failedMessages=" + failedMessages +
                    ", messageTypeStats=" + messageTypeStats +
                    ", sessionTypeStats=" + sessionTypeStats +
                    ", routeStrategyStats=" + routeStrategyStats +
                    ", successRate=" + successRate +
                    ", lastUpdateTime=" + lastUpdateTime +
                    '}';
        }
    }
    
    /**
     * 实时统计信息
     */
    class RealtimeStats {
        private long messagesPerSecond;
        private long messagesPerMinute;
        private long messagesPerHour;
        private long currentConnections;
        private long onlineUsers;
        private double avgProcessingTime;
        private long lastUpdateTime;
        
        public RealtimeStats() {
            this.lastUpdateTime = System.currentTimeMillis();
        }
        
        // Getter and Setter
        public long getMessagesPerSecond() {
            return messagesPerSecond;
        }
        
        public void setMessagesPerSecond(long messagesPerSecond) {
            this.messagesPerSecond = messagesPerSecond;
        }
        
        public long getMessagesPerMinute() {
            return messagesPerMinute;
        }
        
        public void setMessagesPerMinute(long messagesPerMinute) {
            this.messagesPerMinute = messagesPerMinute;
        }
        
        public long getMessagesPerHour() {
            return messagesPerHour;
        }
        
        public void setMessagesPerHour(long messagesPerHour) {
            this.messagesPerHour = messagesPerHour;
        }
        
        public long getCurrentConnections() {
            return currentConnections;
        }
        
        public void setCurrentConnections(long currentConnections) {
            this.currentConnections = currentConnections;
        }
        
        public long getOnlineUsers() {
            return onlineUsers;
        }
        
        public void setOnlineUsers(long onlineUsers) {
            this.onlineUsers = onlineUsers;
        }
        
        public double getAvgProcessingTime() {
            return avgProcessingTime;
        }
        
        public void setAvgProcessingTime(double avgProcessingTime) {
            this.avgProcessingTime = avgProcessingTime;
        }
        
        public long getLastUpdateTime() {
            return lastUpdateTime;
        }
        
        public void setLastUpdateTime(long lastUpdateTime) {
            this.lastUpdateTime = lastUpdateTime;
        }
        
        @Override
        public String toString() {
            return "RealtimeStats{" +
                    "messagesPerSecond=" + messagesPerSecond +
                    ", messagesPerMinute=" + messagesPerMinute +
                    ", messagesPerHour=" + messagesPerHour +
                    ", currentConnections=" + currentConnections +
                    ", onlineUsers=" + onlineUsers +
                    ", avgProcessingTime=" + avgProcessingTime +
                    ", lastUpdateTime=" + lastUpdateTime +
                    '}';
        }
    }
}
