package com.cheeseocean.im.common.entity;

/**
 * @author xxxcrel
 * Created on 2022/5/22
 */
public class MemberEnterTips {
    private GroupInfo group;
    private GroupMemberFullInfo entrantUser;
    private long operationTime;

    public GroupInfo getGroup() {
        return group;
    }

    public void setGroup(GroupInfo group) {
        this.group = group;
    }

    public GroupMemberFullInfo getEntrantUser() {
        return entrantUser;
    }

    public void setEntrantUser(GroupMemberFullInfo entrantUser) {
        this.entrantUser = entrantUser;
    }

    public long getOperationTime() {
        return operationTime;
    }

    public void setOperationTime(long operationTime) {
        this.operationTime = operationTime;
    }
}
