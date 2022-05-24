package com.cheeseocean.cheeseim.base.entity;

/**
 * @author xxxcrel
 * Created on 2022/5/22
 */
public class MemberQuitTips {
    private GroupInfo group;
    private GroupMemberFullInfo quitUser;
    private long operationTime;

    public GroupInfo getGroup() {
        return group;
    }

    public void setGroup(GroupInfo group) {
        this.group = group;
    }

    public GroupMemberFullInfo getQuitUser() {
        return quitUser;
    }

    public void setQuitUser(GroupMemberFullInfo quitUser) {
        this.quitUser = quitUser;
    }

    public long getOperationTime() {
        return operationTime;
    }

    public void setOperationTime(long operationTime) {
        this.operationTime = operationTime;
    }
}
