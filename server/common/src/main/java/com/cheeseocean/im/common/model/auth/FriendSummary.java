package com.cheeseocean.im.common.model.auth;

import java.io.Serializable;

public class FriendSummary implements Serializable {

    private String userId;
    private String displayName;
    private String avatarSeed;

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
}
