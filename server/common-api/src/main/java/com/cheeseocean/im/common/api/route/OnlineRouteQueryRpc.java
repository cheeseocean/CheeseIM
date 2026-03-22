package com.cheeseocean.im.common.api.route;

import com.cheeseocean.im.common.dto.RouteSnapshot;

import java.util.List;

public interface OnlineRouteQueryRpc {

    List<RouteSnapshot> findByUser(String userId);
}
