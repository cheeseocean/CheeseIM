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
    private String          remoteIp;

    public boolean isAuthenticated() {
        return ConnectionState.AUTHENTICATED == state;
    }

}
