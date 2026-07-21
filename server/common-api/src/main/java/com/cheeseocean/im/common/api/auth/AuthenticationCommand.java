package com.cheeseocean.im.common.api.auth;

/** 认证领域命令。 */
public class AuthenticationCommand {
    private String userId;
    private String identityAssertion;
    private Integer platformId;
    private String deviceId;
    private String clientVersion;
    private String refreshToken;
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getIdentityAssertion() { return identityAssertion; }
    public void setIdentityAssertion(String identityAssertion) { this.identityAssertion = identityAssertion; }
    public Integer getPlatformId() { return platformId; }
    public void setPlatformId(Integer platformId) { this.platformId = platformId; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getClientVersion() { return clientVersion; }
    public void setClientVersion(String clientVersion) { this.clientVersion = clientVersion; }
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
}
