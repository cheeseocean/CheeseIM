package com.cheeseocean.im.common.api.session;

import com.cheeseocean.im.common.api.enums.SessionStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;

public class SessionPrincipal implements Serializable {

    private String userId;
    private String tenantId;
    private String sessionId;
    private String deviceId;
    private String platform;
    private String clientVersion;
    private Long tokenVersion;
    private Long permissionVersion;
    private Long passwordVersion;
    private SessionStatus status;
    private Long loginAt;
    private Long lastActiveAt;

    @JsonIgnore
    public boolean isActive() {
        return SessionStatus.ACTIVE == status;
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

    public String getClientVersion() {
        return clientVersion;
    }

    public void setClientVersion(String clientVersion) {
        this.clientVersion = clientVersion;
    }

    public Long getTokenVersion() {
        return tokenVersion;
    }

    public void setTokenVersion(Long tokenVersion) {
        this.tokenVersion = tokenVersion;
    }

    public Long getPermissionVersion() {
        return permissionVersion;
    }

    public void setPermissionVersion(Long permissionVersion) {
        this.permissionVersion = permissionVersion;
    }

    public Long getPasswordVersion() {
        return passwordVersion;
    }

    public void setPasswordVersion(Long passwordVersion) {
        this.passwordVersion = passwordVersion;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public void setStatus(SessionStatus status) {
        this.status = status;
    }

    public Long getLoginAt() {
        return loginAt;
    }

    public void setLoginAt(Long loginAt) {
        this.loginAt = loginAt;
    }

    public Long getLastActiveAt() {
        return lastActiveAt;
    }

    public void setLastActiveAt(Long lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }
}
