package com.cheeseocean.im.common.model.auth;

import java.io.Serializable;

public class PermissionCheckResult implements Serializable {

    private boolean allowed;
    private String code;
    private String message;

    public static PermissionCheckResult allow() {
        PermissionCheckResult result = new PermissionCheckResult();
        result.allowed = true;
        result.code = "OK";
        result.message = "allowed";
        return result;
    }

    public static PermissionCheckResult deny(String code, String message) {
        PermissionCheckResult result = new PermissionCheckResult();
        result.allowed = false;
        result.code = code;
        result.message = message;
        return result;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
