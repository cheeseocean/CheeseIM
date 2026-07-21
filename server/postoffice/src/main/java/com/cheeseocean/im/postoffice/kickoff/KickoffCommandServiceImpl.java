package com.cheeseocean.im.postoffice.kickoff;

import com.cheeseocean.im.common.api.connection.KickoffCommandService;
import com.cheeseocean.im.common.api.dto.route.RouteSnapshot;
import com.cheeseocean.im.common.api.dto.user.KickoffCommand;
import com.cheeseocean.im.common.core.logging.CommonLoggers;
import com.cheeseocean.im.postoffice.config.NodeIdentityProvider;
import com.cheeseocean.im.postoffice.connection.ConnectionManager;
import com.cheeseocean.im.postoffice.service.OnlineRouteService;
import org.apache.dubbo.config.annotation.DubboService;
import org.slf4j.Logger;
import org.springframework.beans.factory.ObjectProvider;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@DubboService
public class KickoffCommandServiceImpl implements KickoffCommandService {

    private static final Logger log = CommonLoggers.POSTOFFICE;

    private final ConnectionManager connectionManager;
    private final OnlineRouteService onlineRouteService;
    private final NodeIdentityProvider nodeIdentityProvider;
    private final NodeCommandPublisher nodeCommandPublisher;

    public KickoffCommandServiceImpl(ConnectionManager connectionManager,
                                     ObjectProvider<OnlineRouteService> onlineRouteService,
                                     NodeIdentityProvider nodeIdentityProvider,
                                     ObjectProvider<NodeCommandPublisher> nodeCommandPublisher) {
        this.connectionManager = connectionManager;
        this.onlineRouteService = onlineRouteService.getIfAvailable();
        this.nodeIdentityProvider = nodeIdentityProvider;
        this.nodeCommandPublisher = nodeCommandPublisher.getIfAvailable();
    }

    @Override
    public void kickoffBySession(KickoffCommand command) {
        if (command == null || command.getSessionId() == null || command.getSessionId().isBlank()) {
            return;
        }
        Set<String> gatewayNodes = findSessionGatewayNodes(command.getSessionId());
        if (gatewayNodes.isEmpty()) {
            connectionManager.kickSessionConnections(command.getSessionId(), command.getReason());
            return;
        }
        ensureAllNodesRouted(gatewayNodes, command);
    }

    @Override
    public void kickoffByUser(KickoffCommand command) {
        if (command == null || command.getUserId() == null || command.getUserId().isBlank()) {
            return;
        }
        Set<String> gatewayNodes = findUserGatewayNodes(command.getUserId(), null);
        if (gatewayNodes.isEmpty()) {
            connectionManager.kickUserConnections(command.getUserId(), command.getReason());
            return;
        }
        ensureAllNodesRouted(gatewayNodes, command);
    }

    @Override
    public void kickoffByDevice(KickoffCommand command) {
        if (command == null || command.getUserId() == null || command.getUserId().isBlank()
                || command.getDeviceId() == null || command.getDeviceId().isBlank()) {
            return;
        }
        Set<String> gatewayNodes = findUserGatewayNodes(command.getUserId(), command.getDeviceId());
        if (gatewayNodes.isEmpty()) {
            connectionManager.kickDeviceConnections(command.getUserId(), command.getDeviceId(), command.getReason());
            return;
        }
        ensureAllNodesRouted(gatewayNodes, command);
    }

    private Set<String> findSessionGatewayNodes(String sessionId) {
        if (onlineRouteService == null) {
            return Set.of();
        }
        try {
            return collectGatewayNodes(onlineRouteService.findBySession(sessionId), null);
        } catch (Exception e) {
            log.warn("Kickoff route lookup by session failed, sessionId={}", sessionId, e);
            return Set.of();
        }
    }

    private Set<String> findUserGatewayNodes(String userId, String deviceId) {
        if (onlineRouteService == null) {
            return Set.of();
        }
        try {
            return collectGatewayNodes(onlineRouteService.findByUser(userId), deviceId);
        } catch (Exception e) {
            log.warn("Kickoff route lookup by user failed, userId={}, deviceId={}", userId, deviceId, e);
            return Set.of();
        }
    }

    private Set<String> collectGatewayNodes(List<RouteSnapshot> routes, String deviceId) {
        if (routes == null || routes.isEmpty()) {
            return Set.of();
        }
        Set<String> nodes = new LinkedHashSet<>();
        for (RouteSnapshot route : routes) {
            if (route == null || route.getGatewayNode() == null || route.getGatewayNode().isBlank()) {
                continue;
            }
            if (deviceId == null || deviceId.equals(route.getDeviceId())) {
                nodes.add(route.getGatewayNode());
            }
        }
        return nodes;
    }

    private void ensureAllNodesRouted(Set<String> gatewayNodes, KickoffCommand command) {
        Set<String> failedNodes = new LinkedHashSet<>();
        for (String gatewayNode : gatewayNodes) {
            boolean routed = routeToNode(gatewayNode, command);
            if (!routed) {
                failedNodes.add(gatewayNode);
                log.warn("Kickoff route failed for nodeId={}, userId={}, sessionId={}, deviceId={}",
                        gatewayNode, command.getUserId(), command.getSessionId(), command.getDeviceId());
            }
        }
        if (!failedNodes.isEmpty()) {
            executeLocal(command);
            throw new IllegalStateException("Kickoff route failed for nodes: " + failedNodes);
        }
    }

    private boolean routeToNode(String gatewayNode, KickoffCommand command) {
        if (gatewayNode == null || gatewayNode.isBlank()) {
            return false;
        }
        if (gatewayNode.equals(currentNodeId())) {
            executeLocal(command);
            return true;
        }
        if (nodeCommandPublisher == null) {
            log.warn("NodeCommandPublisher unavailable, cannot route kickoff to nodeId={}, userId={}, sessionId={}",
                    gatewayNode, command.getUserId(), command.getSessionId());
            return false;
        }
        return nodeCommandPublisher.publishKickoff(gatewayNode, command);
    }

    private void executeLocal(KickoffCommand command) {
        if (command.getConnectionId() != null && !command.getConnectionId().isBlank()) {
            connectionManager.kickConnectionById(
                    command.getConnectionId(),
                    command.getLoginLeaseGeneration(),
                    command.getReason());
        } else if (command.getDeviceId() != null && !command.getDeviceId().isBlank()
                && command.getUserId() != null && !command.getUserId().isBlank()) {
            connectionManager.kickDeviceConnections(command.getUserId(), command.getDeviceId(), command.getReason());
        } else if (command.getSessionId() != null && !command.getSessionId().isBlank()) {
            connectionManager.kickSessionConnections(command.getSessionId(), command.getReason());
        } else if (command.getUserId() != null && !command.getUserId().isBlank()) {
            connectionManager.kickUserConnections(command.getUserId(), command.getReason());
        }
    }

    private String currentNodeId() {
        return nodeIdentityProvider.getNodeId();
    }
}
