package com.cheeseocean.im.common.api.dto.dispatch;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class DispatchMessageReq implements Serializable {

    private static final long serialVersionUID = 1L;

    private String userId;
    private List<String> connectionIds = new ArrayList<>();
    private DispatchPayload payload;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public List<String> getConnectionIds() {
        return connectionIds;
    }

    public void setConnectionIds(List<String> connectionIds) {
        this.connectionIds = connectionIds == null ? new ArrayList<>() : new ArrayList<>(connectionIds);
    }

    public DispatchPayload getPayload() {
        return payload;
    }

    public void setPayload(DispatchPayload payload) {
        this.payload = payload;
    }
}
