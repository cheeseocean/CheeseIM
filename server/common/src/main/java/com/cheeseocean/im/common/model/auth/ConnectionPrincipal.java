package com.cheeseocean.im.common.model.auth;

import java.io.Serializable;

public class ConnectionPrincipal implements Serializable {

    private String connId;
    private String userId;
    private String tenantId;
    private String sessionId;
    private String deviceId;
    private String platform;
    private Long tokenVersion;

    public String getConnId() {
        return connId;
    }

    public void setConnId(String connId) {
        this.connId = connId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public Long getTokenVersion() {
        return tokenVersion;
    }

    public void setTokenVersion(Long tokenVersion) {
        this.tokenVersion = tokenVersion;
    }
}
