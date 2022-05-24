package com.cheeseocean.cheeseim.base.entity;

/**
 * @author xxxcrel
 * Created on 2022/5/20
 */
public class GroupRequest {
    private PublicUserInfo userInfo;
    private GroupInfo groupInfo;
    private int handleResult;
    private String reqMsg;
    private String handleMsg;
    private int reqTime;
    private String handleUserID;
    private int handleTime;
    private String ex;

    public PublicUserInfo getUserInfo() {
        return userInfo;
    }

    public void setUserInfo(PublicUserInfo userInfo) {
        this.userInfo = userInfo;
    }

    public GroupInfo getGroupInfo() {
        return groupInfo;
    }

    public void setGroupInfo(GroupInfo groupInfo) {
        this.groupInfo = groupInfo;
    }

    public int getHandleResult() {
        return handleResult;
    }

    public void setHandleResult(int handleResult) {
        this.handleResult = handleResult;
    }

    public String getReqMsg() {
        return reqMsg;
    }

    public void setReqMsg(String reqMsg) {
        this.reqMsg = reqMsg;
    }

    public String getHandleMsg() {
        return handleMsg;
    }

    public void setHandleMsg(String handleMsg) {
        this.handleMsg = handleMsg;
    }

    public int getReqTime() {
        return reqTime;
    }

    public void setReqTime(int reqTime) {
        this.reqTime = reqTime;
    }

    public String getHandleUserID() {
        return handleUserID;
    }

    public void setHandleUserID(String handleUserID) {
        this.handleUserID = handleUserID;
    }

    public int getHandleTime() {
        return handleTime;
    }

    public void setHandleTime(int handleTime) {
        this.handleTime = handleTime;
    }

    public String getEx() {
        return ex;
    }

    public void setEx(String ex) {
        this.ex = ex;
    }
}
