package com.cheeseocean.im.postoffice.service;

import com.cheeseocean.im.common.dto.RouteSnapshot;

import java.util.List;

public interface OnlineRouteService {

    void register(RouteSnapshot snapshot);

    void refresh(String userId, String deviceId, long heartbeatAt);

    void unregister(String userId, String deviceId);

    List<RouteSnapshot> findByUser(String userId);
}
