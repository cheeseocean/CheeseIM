package com.cheeseocean.im.common.api.session;

import com.cheeseocean.im.common.api.enums.SessionStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;

@Data
public class SessionPrincipal implements Serializable {

    private String        userId;
    private String        tenantId;
    private String        sessionId;
    private String        deviceId;
    private String        platform;
    private String        clientVersion;
    private Long          tokenVersion;
    private Long          permissionVersion;
    private Long          passwordVersion;
    private SessionStatus status;
    private Long          loginAt;
    private Long          lastActiveAt;

    @JsonIgnore
    public boolean isActive() {
        return SessionStatus.ACTIVE == status;
    }
}
