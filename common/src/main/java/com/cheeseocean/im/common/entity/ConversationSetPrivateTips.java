package com.cheeseocean.im.common.entity;

/**
 * @author xxxcrel
 * Created on 2022/5/22
 */
public class ConversationSetPrivateTips {
    private String recvID;
    private String sendID;
    private boolean isPrivate;

    public String getRecvID() {
        return recvID;
    }

    public void setRecvID(String recvID) {
        this.recvID = recvID;
    }

    public String getSendID() {
        return sendID;
    }

    public void setSendID(String sendID) {
        this.sendID = sendID;
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    public void setPrivate(boolean aPrivate) {
        isPrivate = aPrivate;
    }
}
