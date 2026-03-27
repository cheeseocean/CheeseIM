package com.cheeseocean.im.postman.entity;

import java.io.Serializable;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 用户离线推送配置类
 * 
 * @author xxxcrel
 */
public class OfflinePushConfig implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 用户ID
     */
    private String userID;
    
    /**
     * 是否启用离线推送
     */
    private boolean enabled;
    
    /**
     * 每日推送上限
     */
    private Integer maxDailyCount;
    
    /**
     * 当前每日推送计数
     */
    private Integer currentDailyCount;
    
    /**
     * 免打扰开始时间（格式：HH:mm）
     */
    private String quietStartTime;
    
    /**
     * 免打扰结束时间（格式：HH:mm）
     */
    private String quietEndTime;
    
    /**
     * 免打扰期间是否允许推送
     */
    private boolean allowDuringQuietTime;
    
    /**
     * 最后更新时间
     */
    private Long lastUpdateTime;
    
    /**
     * 默认构造函数
     */
    public OfflinePushConfig() {
        this.enabled = true;
        this.maxDailyCount = 100;
        this.currentDailyCount = 0;
        this.quietStartTime = "22:00";
        this.quietEndTime = "08:00";
        this.allowDuringQuietTime = false;
        this.lastUpdateTime = System.currentTimeMillis();
    }
    
    /**
     * 构造函数
     */
    public OfflinePushConfig(String userID) {
        this();
        this.userID = userID;
    }
    
    /**
     * 检查是否在免打扰时间
     */
    public boolean isInQuietTime() {
        if (quietStartTime == null || quietEndTime == null) {
            return false;
        }
        
        try {
            LocalTime now = LocalTime.now();
            LocalTime startTime = LocalTime.parse(quietStartTime, DateTimeFormatter.ofPattern("HH:mm"));
            LocalTime endTime = LocalTime.parse(quietEndTime, DateTimeFormatter.ofPattern("HH:mm"));
            
            // 处理跨天的情况（如22:00-08:00）
            if (startTime.isAfter(endTime)) {
                return now.isAfter(startTime) || now.isBefore(endTime);
            } else {
                return now.isAfter(startTime) && now.isBefore(endTime);
            }
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 检查是否达到每日推送上限
     */
    public boolean isReachedDailyLimit() {
        return currentDailyCount != null && maxDailyCount != null && 
               currentDailyCount >= maxDailyCount;
    }
    
    /**
     * 重置每日推送计数
     */
    public void resetDailyCount() {
        this.currentDailyCount = 0;
        this.lastUpdateTime = System.currentTimeMillis();
    }
    
    // Getter and Setter methods
    public String getUserID() {
        return userID;
    }
    
    public void setUserID(String userID) {
        this.userID = userID;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public Integer getMaxDailyCount() {
        return maxDailyCount;
    }
    
    public void setMaxDailyCount(Integer maxDailyCount) {
        this.maxDailyCount = maxDailyCount;
    }
    
    public Integer getCurrentDailyCount() {
        return currentDailyCount;
    }
    
    public void setCurrentDailyCount(Integer currentDailyCount) {
        this.currentDailyCount = currentDailyCount;
    }
    
    public String getQuietStartTime() {
        return quietStartTime;
    }
    
    public void setQuietStartTime(String quietStartTime) {
        this.quietStartTime = quietStartTime;
    }
    
    public String getQuietEndTime() {
        return quietEndTime;
    }
    
    public void setQuietEndTime(String quietEndTime) {
        this.quietEndTime = quietEndTime;
    }
    
    public boolean isAllowDuringQuietTime() {
        return allowDuringQuietTime;
    }
    
    public void setAllowDuringQuietTime(boolean allowDuringQuietTime) {
        this.allowDuringQuietTime = allowDuringQuietTime;
    }
    
    public Long getLastUpdateTime() {
        return lastUpdateTime;
    }
    
    public void setLastUpdateTime(Long lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }
    
    @Override
    public String toString() {
        return "OfflinePushConfig{" +
                "userID='" + userID + '\'' +
                ", enabled=" + enabled +
                ", maxDailyCount=" + maxDailyCount +
                ", currentDailyCount=" + currentDailyCount +
                ", quietStartTime='" + quietStartTime + '\'' +
                ", quietEndTime='" + quietEndTime + '\'' +
                ", allowDuringQuietTime=" + allowDuringQuietTime +
                ", lastUpdateTime=" + lastUpdateTime +
                '}';
    }
}
