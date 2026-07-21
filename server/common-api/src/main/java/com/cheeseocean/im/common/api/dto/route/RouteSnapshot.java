package com.cheeseocean.im.common.api.dto.route;

import java.io.Serializable;

public class RouteSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;
    /** 当前节点支持在线投递结果回传协议 v1；旧节点缺失该字段时按 0 处理。 */
    public static final int DELIVERY_OUTCOME_VERSION_1 = 1;
    /** 当前节点支持全局 login lease generation。 */
    public static final int LOGIN_LEASE_VERSION_1 = 1;

    private String userId;
    private String connectionId;
    private String sessionId;
    private String deviceId;
    /**
     * 稳定客户端平台 code，供跨节点多端登录策略判定；旧路由缺失时为 null。
     */
    private Integer platformId;
    private Integer loginLeaseVersion;
    private Long loginLeaseGeneration;
    private String gatewayNode;
    /**
     * 在线投递结果回传协议版本。
     *
     * <p>该能力跟随连接路由快照发布，使 postman 在滚动升级期间不会等待旧 postoffice
     * 永远不会产生的结果事件。</p>
     */
    private Integer deliveryOutcomeVersion;
    private Long connectedAt;
    private Long heartbeatAt;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(String connectionId) {
        this.connectionId = connectionId;
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

    public Integer getLoginLeaseVersion() {
        return loginLeaseVersion;
    }

    public void setLoginLeaseVersion(Integer loginLeaseVersion) {
        this.loginLeaseVersion = loginLeaseVersion;
    }

    public Long getLoginLeaseGeneration() {
        return loginLeaseGeneration;
    }

    public void setLoginLeaseGeneration(Long loginLeaseGeneration) {
        this.loginLeaseGeneration = loginLeaseGeneration;
    }

    public String getGatewayNode() {
        return gatewayNode;
    }

    public void setGatewayNode(String gatewayNode) {
        this.gatewayNode = gatewayNode;
    }

    public Integer getDeliveryOutcomeVersion() {
        return deliveryOutcomeVersion;
    }

    public void setDeliveryOutcomeVersion(Integer deliveryOutcomeVersion) {
        this.deliveryOutcomeVersion = deliveryOutcomeVersion;
    }

    public boolean supportsDeliveryOutcomeV1() {
        return deliveryOutcomeVersion != null
                && deliveryOutcomeVersion >= DELIVERY_OUTCOME_VERSION_1;
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
