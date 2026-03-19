package com.cheeseocean.im.authcenter.model;

public class AuthLoginRequest {

    private String userId;
    private Integer platformId;
    private String deviceId;
    private String clientVersion;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Integer getPlatformId() {
        return platformId;
    }

    public void setPlatformId(Integer platformId) {
        this.platformId = platformId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getClientVersion() {
        return clientVersion;
    }

    public void setClientVersion(String clientVersion) {
        this.clientVersion = clientVersion;
    }
}
