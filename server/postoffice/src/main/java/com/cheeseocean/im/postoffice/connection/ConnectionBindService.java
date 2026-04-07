package com.cheeseocean.im.postoffice.connection;

import com.cheeseocean.im.common.api.session.SessionPrincipal;
import com.cheeseocean.im.common.api.enums.ConnectionState;
import com.cheeseocean.im.common.api.enums.PlatformType;
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
        PlatformType platformType = PlatformType.fromName(session.getPlatform());
        context.setConnId(connection.getConnectionID());
        context.setUserId(session.getUserId());
        context.setTenantId(session.getTenantId());
        context.setSessionId(session.getSessionId());
        context.setDeviceId(session.getDeviceId());
        context.setPlatformCode(platformType);
        context.setClientVersion(session.getClientVersion());
        context.setTokenVersion(session.getTokenVersion());
        context.setConnectedAt(connection.getConnectTime());
        context.setLastHeartbeatAt(connection.getLastActiveTime());
        context.setState(ConnectionState.AUTHENTICATED);

        connection.setUserID(session.getUserId());
        connection.setTokenVersion(session.getTokenVersion());
        connection.setDeviceId(session.getDeviceId());
        connection.setPlatformType(platformType);
        connection.setSessionId(session.getSessionId());
        connection.setTenantId(session.getTenantId());
        connection.setPlatform(session.getPlatform());
        connection.setAuthenticated("ws-ticket");

        return connectionManager.addConnection(connection);
    }


}
