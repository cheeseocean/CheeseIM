package com.cheeseocean.im.common.entity;

import java.util.List;

/**
 * @author xxxcrel
 * Created on 2022/5/22
 */
public class MemberKickedTips {
    private GroupInfo group;
    private GroupMemberFullInfo opUser;
    private List<GroupMemberFullInfo> kickedUserList;
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

    public List<GroupMemberFullInfo> getKickedUserList() {
        return kickedUserList;
    }

    public void setKickedUserList(List<GroupMemberFullInfo> kickedUserList) {
        this.kickedUserList = kickedUserList;
    }

    public long getOperationTime() {
        return operationTime;
    }

    public void setOperationTime(long operationTime) {
        this.operationTime = operationTime;
    }
}
