package com.cheeseocean.im.common.entity;

/**
 * @author xxxcrel
 * Created on 2022/5/22
 */
public class FriendAddedTips {
    private FriendInfo friend;
    private long operationTime;
    private PublicUserInfo opUser;

    public FriendInfo getFriend() {
        return friend;
    }

    public void setFriend(FriendInfo friend) {
        this.friend = friend;
    }

    public long getOperationTime() {
        return operationTime;
    }

    public void setOperationTime(long operationTime) {
        this.operationTime = operationTime;
    }

    public PublicUserInfo getOpUser() {
        return opUser;
    }

    public void setOpUser(PublicUserInfo opUser) {
        this.opUser = opUser;
    }
}
