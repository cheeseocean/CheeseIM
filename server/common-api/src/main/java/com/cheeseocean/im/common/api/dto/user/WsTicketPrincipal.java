package com.cheeseocean.im.common.api.dto.user;

import java.io.Serializable;

public class WsTicketPrincipal implements Serializable {

    private String ticket;
    private String userId;
    private String tenantId;
    private String sessionId;
    private String deviceId;
    private String platform;
    private Long tokenVersion;
    private Long expireAt;
    private boolean used;

    public boolean isExpired(long now) {
        return expireAt != null && expireAt <= now;
    }

    public String getTicket() {
        return ticket;
    }

    public void setTicket(String ticket) {
        this.ticket = ticket;
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

    public Long getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(Long expireAt) {
        this.expireAt = expireAt;
    }

    public boolean isUsed() {
        return used;
    }

    public void setUsed(boolean used) {
        this.used = used;
    }
}
