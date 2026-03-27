package com.cheeseocean.im.postoffice.kickoff;

import com.cheeseocean.im.common.api.connection.KickoffCommandService;
import com.cheeseocean.im.common.core.auth.KickoffCommand;
import com.cheeseocean.im.postoffice.connection.ConnectionManager;
import org.apache.dubbo.config.annotation.DubboService;

@DubboService
public class KickoffCommandServiceImpl implements KickoffCommandService {

    private final ConnectionManager connectionManager;

    public KickoffCommandServiceImpl(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public void kickoffBySession(KickoffCommand command) {
        if (command == null || command.getSessionId() == null || command.getSessionId().isBlank()) {
            return;
        }
        connectionManager.kickSessionConnections(command.getSessionId(), command.getReason());
    }

    @Override
    public void kickoffByUser(KickoffCommand command) {
        if (command == null || command.getUserId() == null || command.getUserId().isBlank()) {
            return;
        }
        connectionManager.kickUserConnections(command.getUserId(), command.getReason());
    }

    @Override
    public void kickoffByDevice(KickoffCommand command) {
        if (command == null || command.getUserId() == null || command.getUserId().isBlank()
                || command.getDeviceId() == null || command.getDeviceId().isBlank()) {
            return;
        }
        connectionManager.kickDeviceConnections(command.getUserId(), command.getDeviceId(), command.getReason());
    }
}
