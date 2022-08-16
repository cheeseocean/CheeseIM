package com.cheeseocean.im.base.entity;

/**
 * @author xxxcrel
 * Created on 2022/5/22
 */
public class FriendApplication {
    private long addTime;
    private String addSource;
    private String addWording;

    public long getAddTime() {
        return addTime;
    }

    public void setAddTime(long addTime) {
        this.addTime = addTime;
    }

    public String getAddSource() {
        return addSource;
    }

    public void setAddSource(String addSource) {
        this.addSource = addSource;
    }

    public String getAddWording() {
        return addWording;
    }

    public void setAddWording(String addWording) {
        this.addWording = addWording;
    }
}
