package com.cheeseocean.im.base.entity;

/**
 * @author xxxcrel
 * Created on 2022/5/20
 */
public class OfflinePushInfo {
    private String title;
    private String desc;
    private String ex;
    private String IOSPushSound;
    private boolean IOSBadgeCount;

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

    public String getIOSPushSound() {
        return IOSPushSound;
    }

    public void setIOSPushSound(String IOSPushSound) {
        this.IOSPushSound = IOSPushSound;
    }

    public boolean isIOSBadgeCount() {
        return IOSBadgeCount;
    }

    public void setIOSBadgeCount(boolean IOSBadgeCount) {
        this.IOSBadgeCount = IOSBadgeCount;
    }
}
