package com.cheeseocean.im.authcenter.model;

public class KickoffDeviceRequest {

    private String userId;
    private String deviceId;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }
}
