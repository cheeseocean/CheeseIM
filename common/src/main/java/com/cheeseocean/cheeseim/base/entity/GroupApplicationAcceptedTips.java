package com.cheeseocean.cheeseim.base.entity;

/**
 * @author xxxcrel
 * Created on 2022/5/22
 */
public class GroupApplicationAcceptedTips {
   private GroupInfo group;
   private GroupMemberFullInfo opUser;
   private String handleMsg;

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

    public String getHandleMsg() {
        return handleMsg;
    }

    public void setHandleMsg(String handleMsg) {
        this.handleMsg = handleMsg;
    }
}
