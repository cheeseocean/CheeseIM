package com.cheeseocean.im.common.entity;

/**
 * @author xxxcrel
 * Created on 2022/5/22
 */
public class FriendApplicationApprovedTips {
    private FromToUserID fromToUserID;
    private String handleMsg;

    public FromToUserID getFromToUserID() {
        return fromToUserID;
    }

    public void setFromToUserID(FromToUserID fromToUserID) {
        this.fromToUserID = fromToUserID;
    }

    public String getHandleMsg() {
        return handleMsg;
    }

    public void setHandleMsg(String handleMsg) {
        this.handleMsg = handleMsg;
    }
}
