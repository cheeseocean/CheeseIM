package com.cheeseocean.im.base.entity;

/**
 * @author xxxcrel
 * Created on 2022/5/20
 */
public class GroupMemberFullInfo {
    private String groupID;
    private String userID;
    private int roleLevel;
    private int joinTime;
    private String nickname;
    private String avatarURL;
    private int appManagerLevel;
    private int joinSource;
    private String operatorUserID;
    private String ex;
    private int muteEndTime;

    public String getGroupID() {
        return groupID;
    }

    public void setGroupID(String groupID) {
        this.groupID = groupID;
    }

    public String getUserID() {
        return userID;
    }

    public void setUserID(String userID) {
        this.userID = userID;
    }

    public int getRoleLevel() {
        return roleLevel;
    }

    public void setRoleLevel(int roleLevel) {
        this.roleLevel = roleLevel;
    }

    public int getJoinTime() {
        return joinTime;
    }

    public void setJoinTime(int joinTime) {
        this.joinTime = joinTime;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatarURL() {
        return avatarURL;
    }

    public void setAvatarURL(String avatarURL) {
        this.avatarURL = avatarURL;
    }

    public int getAppManagerLevel() {
        return appManagerLevel;
    }

    public void setAppManagerLevel(int appManagerLevel) {
        this.appManagerLevel = appManagerLevel;
    }

    public int getJoinSource() {
        return joinSource;
    }

    public void setJoinSource(int joinSource) {
        this.joinSource = joinSource;
    }

    public String getOperatorUserID() {
        return operatorUserID;
    }

    public void setOperatorUserID(String operatorUserID) {
        this.operatorUserID = operatorUserID;
    }

    public String getEx() {
        return ex;
    }

    public void setEx(String ex) {
        this.ex = ex;
    }

    public int getMuteEndTime() {
        return muteEndTime;
    }

    public void setMuteEndTime(int muteEndTime) {
        this.muteEndTime = muteEndTime;
    }
}
