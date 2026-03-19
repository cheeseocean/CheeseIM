package com.cheeseocean.im.postoffice.connection;

import com.cheeseocean.im.common.enums.ConnectionState;

import java.io.Serializable;

public class ConnectionContext implements Serializable {

    private String connId;
    private String userId;
    private String tenantId;
    private String sessionId;
    private String deviceId;
    private Integer platformId;
    private String clientVersion;
    private Long tokenVersion;
    private ConnectionState state = ConnectionState.PENDING;
    private Long connectedAt;
    private Long lastHeartbeatAt;
    private String remoteIp;

    public boolean isAuthenticated() {
        return ConnectionState.AUTHENTICATED == state;
    }

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

    public Integer getPlatformId() {
        return platformId;
    }

    public void setPlatformId(Integer platformId) {
        this.platformId = platformId;
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

    public ConnectionState getState() {
        return state;
    }

    public void setState(ConnectionState state) {
        this.state = state;
    }

    public Long getConnectedAt() {
        return connectedAt;
    }

    public void setConnectedAt(Long connectedAt) {
        this.connectedAt = connectedAt;
    }

    public Long getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public void setLastHeartbeatAt(Long lastHeartbeatAt) {
        this.lastHeartbeatAt = lastHeartbeatAt;
    }

    public String getRemoteIp() {
        return remoteIp;
    }

    public void setRemoteIp(String remoteIp) {
        this.remoteIp = remoteIp;
    }
}
