package com.cheeseocean.cheeseim.base.entity;

/**
 * @author xxxcrel
 * Created on 2022/5/22
 */
public class GroupDismissedTips {
    private GroupInfo group;
    private GroupMemberFullInfo opUser;
    private long operationTime;

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
}
