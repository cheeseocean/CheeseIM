package com.cheeseocean.im.base.entity;

/**
 * @author xxxcrel
 * Created on 2022/5/22
 */
public class OrganizationChangedTips {
    private UserInfo opUser;
    private long operationTime;

    public UserInfo getOpUser() {
        return opUser;
    }

    public void setOpUser(UserInfo opUser) {
        this.opUser = opUser;
    }

    public long getOperationTime() {
        return operationTime;
    }

    public void setOperationTime(long operationTime) {
        this.operationTime = operationTime;
    }
}
