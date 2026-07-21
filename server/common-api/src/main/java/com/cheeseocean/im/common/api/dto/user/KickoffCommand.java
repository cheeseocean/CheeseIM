package com.cheeseocean.im.common.api.dto.user;

import java.io.Serializable;

public class KickoffCommand implements Serializable {

    private String userId;
    private String sessionId;
    private String deviceId;
    /**
     * 精确目标连接。存在时优先于 device/session/user，避免重连替换命令误踢新连接。
     */
    private String connectionId;
    /** 登录 lease generation；存在时必须与本地连接匹配后才能执行精确踢线。 */
    private Long loginLeaseGeneration;
    private String reason;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
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

    public String getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(String connectionId) {
        this.connectionId = connectionId;
    }

    public Long getLoginLeaseGeneration() {
        return loginLeaseGeneration;
    }

    public void setLoginLeaseGeneration(Long loginLeaseGeneration) {
        this.loginLeaseGeneration = loginLeaseGeneration;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
