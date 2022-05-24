package com.cheeseocean.cheeseim.base.entity;

/**
 * @author xxxcrel
 * Created on 2022/5/20
 */
public class UserInfo {
    private String userID;
    private String nickname;
    private String avatarURL;
    private int gender;
    private String phoneNumber;
    private int birth;
    private String email;
    private String ex;
    private long createTime;
    private int appMangerLevel;

    public String getUserID() {
        return userID;
    }

    public void setUserID(String userID) {
        this.userID = userID;
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

    public int getGender() {
        return gender;
    }

    public void setGender(int gender) {
        this.gender = gender;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public int getBirth() {
        return birth;
    }

    public void setBirth(int birth) {
        this.birth = birth;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getEx() {
        return ex;
    }

    public void setEx(String ex) {
        this.ex = ex;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public int getAppMangerLevel() {
        return appMangerLevel;
    }

    public void setAppMangerLevel(int appMangerLevel) {
        this.appMangerLevel = appMangerLevel;
    }
}
