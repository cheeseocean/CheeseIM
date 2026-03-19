package com.cheeseocean.im.common.dto;

import java.io.Serializable;

public class RouteSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    private String userId;
    private String deviceId;
    private String gatewayNode;
    private Long connectedAt;
    private Long heartbeatAt;

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

    public String getGatewayNode() {
        return gatewayNode;
    }

    public void setGatewayNode(String gatewayNode) {
        this.gatewayNode = gatewayNode;
    }

    public Long getConnectedAt() {
        return connectedAt;
    }

    public void setConnectedAt(Long connectedAt) {
        this.connectedAt = connectedAt;
    }

    public Long getHeartbeatAt() {
        return heartbeatAt;
    }

    public void setHeartbeatAt(Long heartbeatAt) {
        this.heartbeatAt = heartbeatAt;
    }
}
