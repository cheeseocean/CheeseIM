package com.cheeseocean.im.postoffice.login;

/**
 * Redis 中单条全局登录 lease 的类型化视图。
 */
public record LoginLease(String tenantId,
                         String userId,
                         String connectionId,
                         long generation,
                         String gatewayNode,
                         String deviceId,
                         Integer platformId,
                         String platformClass,
                         String sessionId,
                         long expireAt) {
}
