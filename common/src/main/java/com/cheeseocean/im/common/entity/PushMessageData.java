package com.cheeseocean.im.common.entity;

import java.util.List;

/**
 * 推送消息数据类
 */
public class PushMessageData {
    private Message      message;
    private List<String> targetUsers;
    private Long         pushTime;

    public PushMessageData() {
    }

    public PushMessageData(Message message, List<String> targetUsers) {
        this.message = message;
        this.targetUsers = targetUsers;
        this.pushTime = System.currentTimeMillis();
    }

    // Getter and Setter
    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }

    public List<String> getTargetUsers() {
        return targetUsers;
    }

    public void setTargetUsers(List<String> targetUsers) {
        this.targetUsers = targetUsers;
    }

    public Long getPushTime() {
        return pushTime;
    }

    public void setPushTime(Long pushTime) {
        this.pushTime = pushTime;
    }

    @Override
    public String toString() {
        return "PushMessageData{" +
                "message=" + message +
                ", targetUsers=" + targetUsers +
                ", pushTime=" + pushTime +
                '}';
    }
}
