package com.cheeseocean.im.common.api.dto.message;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Map;

public class OfflinePushInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("title")
    private String title;

    @JsonProperty("desc")
    private String desc;

    @JsonProperty("ex")
    private String ex;

    @JsonProperty("iOSPushSound")
    private String iOSPushSound;

    @JsonProperty("iOSBadgeCount")
    private Boolean iOSBadgeCount;

    @JsonProperty("signalInfo")
    private String signalInfo;

    @JsonProperty("pushExtras")
    private Map<String, Object> pushExtras;

    public OfflinePushInfo() {
        this.iOSPushSound = "default";
        this.iOSBadgeCount = true;
    }

    public OfflinePushInfo(String title, String desc) {
        this();
        this.title = title;
        this.desc = desc;
    }

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
}
