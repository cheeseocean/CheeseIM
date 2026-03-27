package com.cheeseocean.im.postoffice.api;

import com.cheeseocean.im.common.api.route.OnlineRouteQueryService;
import com.cheeseocean.im.common.api.dto.route.RouteSnapshot;
import com.cheeseocean.im.postoffice.service.OnlineRouteService;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.List;

@DubboService
public class OnlineRouteQueryServiceImpl implements OnlineRouteQueryService {

    private final OnlineRouteService onlineRouteService;

    public OnlineRouteQueryServiceImpl(OnlineRouteService onlineRouteService) {
        this.onlineRouteService = onlineRouteService;
    }

    @Override
    public List<RouteSnapshot> findByUser(String userId) {
        return onlineRouteService.findByUser(userId);
    }
}
