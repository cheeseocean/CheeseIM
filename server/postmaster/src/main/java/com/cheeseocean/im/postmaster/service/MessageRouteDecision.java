package com.cheeseocean.im.postmaster.service;

/**
 * ingress 实际执行的路由决策；协议保留选项不在此声明为已实现能力。
 * 离线推送由 postman 直接读取消息选项，不复制到本地决策。
 */
public record MessageRouteDecision(
        boolean persistHistory,
        boolean sendDelivery,
        boolean notification) {
}
