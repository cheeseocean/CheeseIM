package com.cheeseocean.im.authcenter.model;

public class AuthResponse {

    private String userId;
    private String sessionId;
    private String accessToken;
    private String refreshToken;
    private Long accessExpireAt;
    private Long refreshExpireAt;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public Long getAccessExpireAt() {
        return accessExpireAt;
    }

    public void setAccessExpireAt(Long accessExpireAt) {
        this.accessExpireAt = accessExpireAt;
    }

    public Long getRefreshExpireAt() {
        return refreshExpireAt;
    }

    public void setRefreshExpireAt(Long refreshExpireAt) {
        this.refreshExpireAt = refreshExpireAt;
    }
}
