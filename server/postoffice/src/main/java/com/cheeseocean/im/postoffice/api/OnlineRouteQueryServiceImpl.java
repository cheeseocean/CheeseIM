package com.cheeseocean.im.postoffice.api;

import com.cheeseocean.im.common.api.route.OnlineRouteQueryService;
import com.cheeseocean.im.common.api.dto.route.RouteSnapshot;
import com.cheeseocean.im.postoffice.service.OnlineRouteService;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.ArrayList;
import java.util.List;

@DubboService
public class OnlineRouteQueryServiceImpl implements OnlineRouteQueryService {

    private final OnlineRouteService onlineRouteService;

    public OnlineRouteQueryServiceImpl(OnlineRouteService onlineRouteService) {
        this.onlineRouteService = onlineRouteService;
    }

    @Override
    public List<RouteSnapshot> findByUser(String userId) {
        List<RouteSnapshot> routes = onlineRouteService.findByUser(userId);
        // Dubbo injvm 同样按严格模式序列化返回值，List.of/Stream.toList 会携带内部 CollSer。
        return routes == null ? new ArrayList<>() : new ArrayList<>(routes);
    }
}
