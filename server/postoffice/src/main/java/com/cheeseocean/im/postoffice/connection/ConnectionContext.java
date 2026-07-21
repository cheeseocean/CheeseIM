package com.cheeseocean.im.postoffice.connection;

import com.cheeseocean.im.common.api.enums.ConnectionState;
import com.cheeseocean.im.common.api.enums.PlatformType;
import lombok.Data;

import java.io.Serializable;

@Data
public class ConnectionContext implements Serializable {

    private String          connId;
    private String          userId;
    private String          tenantId;
    private String          sessionId;
    private String          deviceId;
    private PlatformType    platformCode;
    private String          clientVersion;
    private Long            tokenVersion;
    private ConnectionState state = ConnectionState.PENDING;
    private Long            connectedAt;
    private Long            lastHeartbeatAt;
    /** 最近一次服务端 session 有效性校验成功时间；只属于本连接的本地租约。 */
    private long            sessionValidatedAt;
    private String          remoteIp;

    public boolean isAuthenticated() {
        return ConnectionState.AUTHENTICATED == state;
    }

}
