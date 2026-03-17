package com.cheeseocean.im.common.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.Map;

/**
 * Offline push metadata carried alongside a message.
 */
public class OfflinePushInfo implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 推送标题
     */
    @JsonProperty("title")
    private String title;
    
    /**
     * 推送内容描述
     */
    @JsonProperty("desc")
    private String desc;
    
    /**
     * 扩展信息
     */
    @JsonProperty("ex")
    private String ex;
    
    /**
     * iOS推送配置
     */
    @JsonProperty("iOSPushSound")
    private String iOSPushSound;
    
    /**
     * iOS角标设置
     */
    @JsonProperty("iOSBadgeCount")
    private Boolean iOSBadgeCount;
    
    /**
     * 信令推送信息
     */
    @JsonProperty("signalInfo")
    private String signalInfo;
    
    /**
     * 推送扩展数据
     */
    @JsonProperty("pushExtras")
    private Map<String, Object> pushExtras;
    
    public OfflinePushInfo() {
        // 设置默认值
        this.iOSPushSound = "default";
        this.iOSBadgeCount = true;
    }
    
    public OfflinePushInfo(String title, String desc) {
        this();
        this.title = title;
        this.desc = desc;
    }
    
    // Getter and Setter methods
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getDesc() {
        return desc;
    }
    
    public void setDesc(String desc) {
        this.desc = desc;
    }
    
    public String getEx() {
        return ex;
    }
    
    public void setEx(String ex) {
        this.ex = ex;
    }
    
    public String getiOSPushSound() {
        return iOSPushSound;
    }
    
    public void setiOSPushSound(String iOSPushSound) {
        this.iOSPushSound = iOSPushSound;
    }
    
    public Boolean getiOSBadgeCount() {
        return iOSBadgeCount;
    }
    
    public void setiOSBadgeCount(Boolean iOSBadgeCount) {
        this.iOSBadgeCount = iOSBadgeCount;
    }
    
    public String getSignalInfo() {
        return signalInfo;
    }
    
    public void setSignalInfo(String signalInfo) {
        this.signalInfo = signalInfo;
    }
    
    public Map<String, Object> getPushExtras() {
        return pushExtras;
    }
    
    public void setPushExtras(Map<String, Object> pushExtras) {
        this.pushExtras = pushExtras;
    }
    
    @Override
    public String toString() {
        return "OfflinePushInfo{" +
                "title='" + title + '\'' +
                ", desc='" + desc + '\'' +
                ", ex='" + ex + '\'' +
                ", iOSPushSound='" + iOSPushSound + '\'' +
                ", iOSBadgeCount=" + iOSBadgeCount +
                ", signalInfo='" + signalInfo + '\'' +
                ", pushExtras=" + pushExtras +
                '}';
    }
}
