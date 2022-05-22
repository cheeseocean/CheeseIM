package com.cheeseocean.openim.base.entity;

/**
 * @author xxxcrel
 * Created on 2022/5/22
 */
public class ParticipantMetaData {
    private GroupInfo groupInfo;
    private GroupMemberFullInfo groupMemberInfo;
    private PublicUserInfo userInfo;

    public GroupInfo getGroupInfo() {
        return groupInfo;
    }

    public void setGroupInfo(GroupInfo groupInfo) {
        this.groupInfo = groupInfo;
    }

    public GroupMemberFullInfo getGroupMemberInfo() {
        return groupMemberInfo;
    }

    public void setGroupMemberInfo(GroupMemberFullInfo groupMemberInfo) {
        this.groupMemberInfo = groupMemberInfo;
    }

    public PublicUserInfo getUserInfo() {
        return userInfo;
    }

    public void setUserInfo(PublicUserInfo userInfo) {
        this.userInfo = userInfo;
    }
}
