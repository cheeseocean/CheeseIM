package com.cheeseocean.im.postoffice.service;

import java.util.List;

/**
 * 路由心跳批量持久化端口。
 */
public interface OnlineRouteHeartbeatWriter {

    void refreshBatch(List<RouteHeartbeat> heartbeats);
}
