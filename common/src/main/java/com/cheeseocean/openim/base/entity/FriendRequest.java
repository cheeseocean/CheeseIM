package com.cheeseocean.openim.base.entity;

/**
 * @author xxxcrel
 * Created on 2022/5/20
 */
public class FriendRequest {
    private String fromUserID;
    private String fromNickname;
    private String fromAvatarURL;
    private int fromGender;
    private String toUserID;
    private String toNickname;
    private String toAvatarURL;
    private int toGender;
    private int handleResult;
    private String reqMsg;
    private long createTime;
    private String handlerUserID;
    private String handleMsg;
    private int handleTime;
    private String ex;

    public String getFromUserID() {
        return fromUserID;
    }

    public void setFromUserID(String fromUserID) {
        this.fromUserID = fromUserID;
    }

    public String getFromNickname() {
        return fromNickname;
    }

    public void setFromNickname(String fromNickname) {
        this.fromNickname = fromNickname;
    }

    public String getFromAvatarURL() {
        return fromAvatarURL;
    }

    public void setFromAvatarURL(String fromAvatarURL) {
        this.fromAvatarURL = fromAvatarURL;
    }

    public int getFromGender() {
        return fromGender;
    }

    public void setFromGender(int fromGender) {
        this.fromGender = fromGender;
    }

    public String getToUserID() {
        return toUserID;
    }

    public void setToUserID(String toUserID) {
        this.toUserID = toUserID;
    }

    public String getToNickname() {
        return toNickname;
    }

    public void setToNickname(String toNickname) {
        this.toNickname = toNickname;
    }

    public String getToAvatarURL() {
        return toAvatarURL;
    }

    public void setToAvatarURL(String toAvatarURL) {
        this.toAvatarURL = toAvatarURL;
    }

    public int getToGender() {
        return toGender;
    }

    public void setToGender(int toGender) {
        this.toGender = toGender;
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

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public String getHandlerUserID() {
        return handlerUserID;
    }

    public void setHandlerUserID(String handlerUserID) {
        this.handlerUserID = handlerUserID;
    }

    public String getHandleMsg() {
        return handleMsg;
    }

    public void setHandleMsg(String handleMsg) {
        this.handleMsg = handleMsg;
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
