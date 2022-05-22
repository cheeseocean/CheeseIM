package com.cheeseocean.openim.base.entity;

import java.util.List;

/**
 * @author xxxcrel
 * Created on 2022/5/22
 */
public class MemberInvitedTips {
    private GroupInfo group;
    private GroupMemberFullInfo opUser;
    private List<GroupMemberFullInfo> invitedUserList;
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

    public List<GroupMemberFullInfo> getInvitedUserList() {
        return invitedUserList;
    }

    public void setInvitedUserList(List<GroupMemberFullInfo> invitedUserList) {
        this.invitedUserList = invitedUserList;
    }

    public long getOperationTime() {
        return operationTime;
    }

    public void setOperationTime(long operationTime) {
        this.operationTime = operationTime;
    }
}
