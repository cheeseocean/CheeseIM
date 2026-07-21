package com.cheeseocean.im.apiserver.model.request;

/** HTTP 登录请求。 */
public class AuthLoginRequest {
    private String userId;
    private String identityAssertion;
    private Integer platformId;
    private String deviceId;
    private String clientVersion;
    public String getUserId() { return userId; } public void setUserId(String value) { userId = value; }
    public String getIdentityAssertion() { return identityAssertion; }
    public void setIdentityAssertion(String value) { identityAssertion = value; }
    public Integer getPlatformId() { return platformId; } public void setPlatformId(Integer value) { platformId = value; }
    public String getDeviceId() { return deviceId; } public void setDeviceId(String value) { deviceId = value; }
    public String getClientVersion() { return clientVersion; } public void setClientVersion(String value) { clientVersion = value; }
}
