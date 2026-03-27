package com.cheeseocean.im.common.api.route;

import com.cheeseocean.im.common.api.dto.route.RouteSnapshot;

import java.util.List;

public interface OnlineRouteQueryService {

    List<RouteSnapshot> findByUser(String userId);
}
