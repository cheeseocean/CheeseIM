package com.cheeseocean.im.common.dto;

import java.io.Serializable;

public class PushResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean success;
    private String userId;
    private String provider;
    private String traceId;

    public static PushResult success(String userId, String provider) {
        PushResult result = new PushResult();
        result.success = true;
        result.userId = userId;
        result.provider = provider;
        return result;
    }

    public static PushResult failed(String userId, String traceId) {
        PushResult result = new PushResult();
        result.success = false;
        result.userId = userId;
        result.traceId = traceId;
        return result;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getUserId() {
        return userId;
    }

    public String getProvider() {
        return provider;
    }

    public String getTraceId() {
        return traceId;
    }
}
