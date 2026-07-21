package com.cheeseocean.im.postoffice.service;

/**
 * 待持久化的连接路由心跳。
 */
public record RouteHeartbeat(String userId,
                             String deviceId,
                             String sessionId,
                             String connectionId,
                             long heartbeatAt) {
}
