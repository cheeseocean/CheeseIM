package com.cheeseocean.cheeseim.base.entity;

import java.util.List;

/**
 * @author xxxcrel
 * Created on 2022/5/22
 */
public class GroupCreatedTips {
   private GroupInfo group;
   private GroupMemberFullInfo opUser;
   private List<GroupMemberFullInfo> memberList;
   private long operationTime;
   private GroupMemberFullInfo groupOwnerUser;

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

    public List<GroupMemberFullInfo> getMemberList() {
        return memberList;
    }

    public void setMemberList(List<GroupMemberFullInfo> memberList) {
        this.memberList = memberList;
    }

    public long getOperationTime() {
        return operationTime;
    }

    public void setOperationTime(long operationTime) {
        this.operationTime = operationTime;
    }

    public GroupMemberFullInfo getGroupOwnerUser() {
        return groupOwnerUser;
    }

    public void setGroupOwnerUser(GroupMemberFullInfo groupOwnerUser) {
        this.groupOwnerUser = groupOwnerUser;
    }
}
