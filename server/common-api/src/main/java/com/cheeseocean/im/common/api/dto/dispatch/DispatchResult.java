package com.cheeseocean.im.common.api.dto.dispatch;

import java.io.Serializable;

public class DispatchResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private String connectionId;
    private boolean success;
    private String code;
    private String message;

    public DispatchResult() {
    }

    public DispatchResult(String connectionId, boolean success, String code, String message) {
        this.connectionId = connectionId;
        this.success = success;
        this.code = code;
        this.message = message;
    }

    public String getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(String connectionId) {
        this.connectionId = connectionId;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
