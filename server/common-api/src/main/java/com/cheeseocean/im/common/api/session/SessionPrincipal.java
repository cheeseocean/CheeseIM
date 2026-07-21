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
    /** 当前会话绑定的 refresh token family，用于整族轮换与撤销。 */
    private String        refreshTokenFamilyId;
    /** refresh token family 的绝对过期时间，轮换不得延长。 */
    private Long          refreshTokenExpireAt;

    @JsonIgnore
    public boolean isActive() {
        return SessionStatus.ACTIVE == status;
    }
}
