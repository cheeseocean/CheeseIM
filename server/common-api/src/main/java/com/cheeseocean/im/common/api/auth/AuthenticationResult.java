package com.cheeseocean.im.common.api.auth;

/** 认证领域结果，不含 HTTP 表达。 */
public class AuthenticationResult {
    private String userId; private String sessionId; private String accessToken; private String refreshToken;
    private Long accessExpireAt; private Long refreshExpireAt;
    public String getUserId() { return userId; } public void setUserId(String value) { userId = value; }
    public String getSessionId() { return sessionId; } public void setSessionId(String value) { sessionId = value; }
    public String getAccessToken() { return accessToken; } public void setAccessToken(String value) { accessToken = value; }
    public String getRefreshToken() { return refreshToken; } public void setRefreshToken(String value) { refreshToken = value; }
    public Long getAccessExpireAt() { return accessExpireAt; } public void setAccessExpireAt(Long value) { accessExpireAt = value; }
    public Long getRefreshExpireAt() { return refreshExpireAt; } public void setRefreshExpireAt(Long value) { refreshExpireAt = value; }
}
