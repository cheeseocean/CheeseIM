package com.cheeseocean.cheeseim.base.entity;

/**
 * @author xxxcrel
 * Created on 2022/5/22
 */
public class GroupMemberMutedTips {
    private GroupInfo group;
    private GroupMemberFullInfo opUser;
    private long operationTime;
    private GroupMemberFullInfo mutedUser;
    private long mutedSeconds;

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

    public GroupMemberFullInfo getMutedUser() {
        return mutedUser;
    }

    public void setMutedUser(GroupMemberFullInfo mutedUser) {
        this.mutedUser = mutedUser;
    }

    public long getMutedSeconds() {
        return mutedSeconds;
    }

    public void setMutedSeconds(long mutedSeconds) {
        this.mutedSeconds = mutedSeconds;
    }
}
