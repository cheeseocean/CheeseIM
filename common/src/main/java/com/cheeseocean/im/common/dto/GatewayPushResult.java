package com.cheeseocean.im.common.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class GatewayPushResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private String receiverId;
    private boolean routeFound;
    private List<String> deliveredDeviceIds = new ArrayList<>();
    private List<String> failedDeviceIds = new ArrayList<>();

    public String getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(String receiverId) {
        this.receiverId = receiverId;
    }

    public boolean isRouteFound() {
        return routeFound;
    }

    public void setRouteFound(boolean routeFound) {
        this.routeFound = routeFound;
    }

    public List<String> getDeliveredDeviceIds() {
        return deliveredDeviceIds;
    }

    public void setDeliveredDeviceIds(List<String> deliveredDeviceIds) {
        this.deliveredDeviceIds = deliveredDeviceIds;
    }

    public List<String> getFailedDeviceIds() {
        return failedDeviceIds;
    }

    public void setFailedDeviceIds(List<String> failedDeviceIds) {
        this.failedDeviceIds = failedDeviceIds;
    }
}
