package com.cheeseocean.im.postoffice.service;

import com.cheeseocean.im.common.api.GatewayPushService;
import com.cheeseocean.im.common.dto.GatewayPushResult;
import com.cheeseocean.im.common.dto.MessageProto;
import com.cheeseocean.im.common.dto.RouteSnapshot;
import com.cheeseocean.im.postoffice.connection.ConnectionManager;
import com.cheeseocean.im.postoffice.connection.UserConnection;
import com.cheeseocean.im.postoffice.protocol.WSMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class GatewayPushServiceImpl implements GatewayPushService {

    private final ConnectionManager connectionManager;
    private final OnlineRouteService onlineRouteService;

    public GatewayPushServiceImpl(ConnectionManager connectionManager, OnlineRouteService onlineRouteService) {
        this.connectionManager = connectionManager;
        this.onlineRouteService = onlineRouteService;
    }

    @Override
    public GatewayPushResult pushToUser(String receiverId, MessageProto message) {
        List<RouteSnapshot> routes = onlineRouteService.findByUser(receiverId);
        GatewayPushResult result = new GatewayPushResult();
        result.setReceiverId(receiverId);
        result.setRouteFound(!routes.isEmpty());

        List<String> delivered = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        for (RouteSnapshot route : routes) {
            if (!connectionManager.markDeliveryIfAbsent(message.getServerMsgId(), receiverId, route.getDeviceId())) {
                continue;
            }
            UserConnection connection = matchConnection(receiverId, route.getDeviceId());
            boolean pushed = connection != null
                    && connectionManager.sendMessageToConnection(connection,
                    WSMessage.recvMsgNotify(message.getServerMsgId(), message));
            if (pushed) {
                delivered.add(route.getDeviceId());
            } else {
                failed.add(route.getDeviceId());
            }
        }

        result.setDeliveredDeviceIds(delivered);
        result.setFailedDeviceIds(failed);
        return result;
    }

    private UserConnection matchConnection(String userId, String deviceId) {
        for (UserConnection connection : connectionManager.getUserConnections(userId)) {
            String platformName = connection.getPlatformName().toLowerCase();
            if (deviceId == null || deviceId.isBlank() || deviceId.startsWith(platformName)) {
                return connection;
            }
        }
        return null;
    }
}
