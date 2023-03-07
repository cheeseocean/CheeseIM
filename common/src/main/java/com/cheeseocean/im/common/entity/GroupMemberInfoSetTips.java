package com.cheeseocean.im.common.entity;

/**
 * @author xxxcrel
 * Created on 2022/5/22
 */
public class GroupMemberInfoSetTips {
    private GroupInfo group;
    private GroupMemberFullInfo opUser;
    private long operationTime;
    private GroupMemberFullInfo changedUser;

    public GroupInfo getGroup() {
        return group;
    }

    public void setGroup(GroupInfo group) {
        this.group = group;
    }

    public GroupMemberFullInfo getOpUser() {
        return opUser;
    }

    public void setOpUser(GroupMemberFullInfo opUser) {
        this.opUser = opUser;
    }

    public long getOperationTime() {
        return operationTime;
    }

    public void setOperationTime(long operationTime) {
        this.operationTime = operationTime;
    }

    public GroupMemberFullInfo getChangedUser() {
        return changedUser;
    }

    public void setChangedUser(GroupMemberFullInfo changedUser) {
        this.changedUser = changedUser;
    }
}
