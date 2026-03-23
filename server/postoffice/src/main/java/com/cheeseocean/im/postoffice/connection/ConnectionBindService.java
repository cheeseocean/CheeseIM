package com.cheeseocean.im.postoffice.connection;

import com.cheeseocean.im.common.core.auth.SessionPrincipal;
import com.cheeseocean.im.common.core.enums.ConnectionState;
import com.cheeseocean.im.common.core.enums.PlatformType;
import org.springframework.stereotype.Service;

@Service
public class ConnectionBindService {

    private final ConnectionManager connectionManager;

    public ConnectionBindService(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    public boolean bindAuthenticated(UserConnection connection, SessionPrincipal session) {
        ConnectionContext context = connection.getContext();
        if (context == null) {
            context = new ConnectionContext();
            connection.setContext(context);
        }
        Integer platformId = resolvePlatformId(session.getPlatform());

        context.setConnId(connection.getConnectionID());
        context.setUserId(session.getUserId());
        context.setTenantId(session.getTenantId());
        context.setSessionId(session.getSessionId());
        context.setDeviceId(session.getDeviceId());
        context.setPlatformId(platformId);
        context.setClientVersion(session.getClientVersion());
        context.setTokenVersion(session.getTokenVersion());
        context.setConnectedAt(connection.getConnectTime());
        context.setLastHeartbeatAt(connection.getLastActiveTime());
        context.setState(ConnectionState.AUTHENTICATED);

        connection.setUserID(session.getUserId());
        connection.setTokenVersion(session.getTokenVersion());
        connection.setDeviceID(session.getDeviceId());
        connection.setPlatformID(platformId);
        connection.setSessionID(session.getSessionId());
        connection.setTenantID(session.getTenantId());
        connection.setPlatform(session.getPlatform());
        connection.setAuthenticated("ws-ticket");

        return connectionManager.addConnection(connection);
    }

    private Integer resolvePlatformId(String platform) {
        PlatformType platformType = PlatformType.fromName(platform);
        return platformType == PlatformType.UNKNOWN ? null : platformType.getCode();
    }
}
