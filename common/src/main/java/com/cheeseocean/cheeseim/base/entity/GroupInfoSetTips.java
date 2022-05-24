package com.cheeseocean.cheeseim.base.entity;

/**
 * @author xxxcrel
 * Created on 2022/5/22
 */
public class GroupInfoSetTips {
    private GroupMemberFullInfo opUser;
    private long muteTime;
    private GroupInfo group;

   public GroupMemberFullInfo getOpUser() {
      return opUser;
   }

   public void setOpUser(GroupMemberFullInfo opUser) {
      this.opUser = opUser;
   }

   public long getMuteTime() {
      return muteTime;
   }

   public void setMuteTime(long muteTime) {
      this.muteTime = muteTime;
   }

   public GroupInfo getGroup() {
      return group;
   }

   public void setGroup(GroupInfo group) {
      this.group = group;
   }
}
