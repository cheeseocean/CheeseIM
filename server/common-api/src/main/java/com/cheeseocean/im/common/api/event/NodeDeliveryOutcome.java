package com.cheeseocean.im.common.api.event;

import com.cheeseocean.im.common.api.dto.dispatch.DispatchResult;
import com.cheeseocean.im.common.api.enums.NodeDeliveryOutcomeCode;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * postoffice 节点投递最终结果事件。
 */
public class NodeDeliveryOutcome implements Serializable {

    private static final long serialVersionUID = 1L;

    private String deliveryId;
    private String userId;
    private String gatewayNode;
    private Integer outcomeCode;
    private List<DispatchResult> results = new ArrayList<>();
    private String failureReason;
    private long occurredAt;

    public String getDeliveryId() {
        return deliveryId;
    }

    public void setDeliveryId(String deliveryId) {
        this.deliveryId = deliveryId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getGatewayNode() {
        return gatewayNode;
    }

    public void setGatewayNode(String gatewayNode) {
        this.gatewayNode = gatewayNode;
    }

    public Integer getOutcomeCode() {
        return outcomeCode;
    }

    public void setOutcomeCode(Integer outcomeCode) {
        this.outcomeCode = outcomeCode;
    }

    public void setOutcomeCode(NodeDeliveryOutcomeCode outcomeCode) {
        this.outcomeCode = outcomeCode == null ? null : outcomeCode.getCode();
    }

    public List<DispatchResult> getResults() {
        return new ArrayList<>(results);
    }

    public void setResults(List<DispatchResult> results) {
        this.results = results == null ? new ArrayList<>() : new ArrayList<>(results);
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public long getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(long occurredAt) {
        this.occurredAt = occurredAt;
    }
}
