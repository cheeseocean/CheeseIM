package com.cheeseocean.im.common.model.auth;

import java.io.Serializable;

public class FriendRequestSummary implements Serializable {

    private String userId;
    private String displayName;
    private String avatarSeed;
    private String status;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getAvatarSeed() {
        return avatarSeed;
    }

    public void setAvatarSeed(String avatarSeed) {
        this.avatarSeed = avatarSeed;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
