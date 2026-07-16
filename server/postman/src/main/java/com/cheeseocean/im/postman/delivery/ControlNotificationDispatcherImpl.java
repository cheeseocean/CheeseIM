package com.cheeseocean.im.postman.delivery;

import com.cheeseocean.im.common.api.dto.dispatch.ControlNotificationReq;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageReq;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageResp;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchPayload;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchResult;
import com.cheeseocean.im.common.api.dto.route.RouteSnapshot;
import com.cheeseocean.im.common.api.route.OnlineRouteQueryService;
import com.cheeseocean.im.common.api.rpc.ControlNotificationDispatcher;
import com.cheeseocean.im.common.api.rpc.NodeDeliveryService;
import com.cheeseocean.im.common.api.rpc.OnlineDispatcher;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 控制通知跨节点投递实现。
 *
 * <p>复用普通在线投递的 gatewayNode 路由和节点队列，但不因为用户离线或投递失败而触发
 * 离线推送。这样已读、撤回等同步控制面只影响指定用户的当前在线端。
 */
@DubboService(retries = 0)
public class ControlNotificationDispatcherImpl implements ControlNotificationDispatcher {

    @DubboReference(check = false)
    private OnlineRouteQueryService onlineRouteQueryService;

    @DubboReference(check = false, retries = 0)
    private OnlineDispatcher onlineDispatcher;

    private final NodeDeliveryService nodeDeliveryService;

    public ControlNotificationDispatcherImpl(ObjectProvider<NodeDeliveryService> nodeDeliveryServiceProvider) {
        this.nodeDeliveryService = nodeDeliveryServiceProvider.getIfAvailable();
    }

    @Override
    public boolean dispatch(ControlNotificationReq request) {
        if (request == null || isBlank(request.getUserId()) || request.getEnvelope() == null
                || isBlank(request.getDeliveryId())) {
            return false;
        }
        List<RouteSnapshot> routes = onlineRouteQueryService.findByUser(request.getUserId());
        if (routes == null || routes.isEmpty()) {
            return false;
        }
        Map<String, List<RouteSnapshot>> routesByNode = routes.stream()
                .filter(route -> route != null)
                .collect(Collectors.groupingBy(route -> hasNode(route) ? route.getGatewayNode() : ""));
        boolean accepted = false;
        for (Map.Entry<String, List<RouteSnapshot>> entry : routesByNode.entrySet()) {
            DispatchMessageReq dispatchRequest = buildDispatchRequest(request, entry.getValue());
            if (!entry.getKey().isEmpty() && nodeDeliveryService != null) {
                accepted |= nodeDeliveryService.deliver(entry.getKey(), dispatchRequest);
            } else {
                accepted |= hasSuccessfulDispatch(onlineDispatcher.dispatchMessage(dispatchRequest));
            }
        }
        return accepted;
    }

    private DispatchMessageReq buildDispatchRequest(ControlNotificationReq request,
                                                    List<RouteSnapshot> routes) {
        DispatchPayload payload = new DispatchPayload();
        payload.setEnvelope(request.getEnvelope());
        payload.setDeliveryId(request.getDeliveryId());
        DispatchMessageReq dispatchRequest = new DispatchMessageReq();
        dispatchRequest.setUserId(request.getUserId());
        dispatchRequest.setPayload(payload);
        dispatchRequest.setConnectionIds(routes.stream()
                .map(RouteSnapshot::getConnectionId)
                .filter(connectionId -> !isBlank(connectionId))
                .toList());
        return dispatchRequest;
    }

    private boolean hasSuccessfulDispatch(DispatchMessageResp response) {
        if (response == null || response.getResults() == null) {
            return false;
        }
        return response.getResults().stream().anyMatch(DispatchResult::isSuccess);
    }

    private boolean hasNode(RouteSnapshot route) {
        return !isBlank(route.getGatewayNode());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
