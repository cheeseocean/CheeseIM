package com.cheeseocean.openim.base.entity;

/**
 * @author xxxcrel
 * Created on 2022/5/20
 */
public class BlackInfo {
    private String ownerUserID;
    private int createTime;
    private PublicUserInfo blackUserInfo;
    private int addSource;
    private String operatorUserID;
    private String ex;

    public String getOwnerUserID() {
        return ownerUserID;
    }

    public void setOwnerUserID(String ownerUserID) {
        this.ownerUserID = ownerUserID;
    }

    public int getCreateTime() {
        return createTime;
    }

    public void setCreateTime(int createTime) {
        this.createTime = createTime;
    }

    public PublicUserInfo getBlackUserInfo() {
        return blackUserInfo;
    }

    public void setBlackUserInfo(PublicUserInfo blackUserInfo) {
        this.blackUserInfo = blackUserInfo;
    }

    public int getAddSource() {
        return addSource;
    }

    public void setAddSource(int addSource) {
        this.addSource = addSource;
    }

    public String getOperatorUserID() {
        return operatorUserID;
    }

    public void setOperatorUserID(String operatorUserID) {
        this.operatorUserID = operatorUserID;
    }

    public String getEx() {
        return ex;
    }

    public void setEx(String ex) {
        this.ex = ex;
    }
}
