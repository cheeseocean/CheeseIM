package com.cheeseocean.im.common.api.rpc;

import com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageReq;

/**
 * 节点投递服务：将在线投递请求发送到目标 postoffice 节点的投递队列。
 *
 * <p>postman 调用此接口将消息路由到持有目标用户连接的 postoffice 节点，
 * 解决跨节点在线投递失效问题（ASSESSMENT P0-1）。
 *
 * <p>实现方应根据 {@code RouteSnapshot.gatewayNode} 选择投递方式：
 * <ul>
 *   <li>Redis 实现（生产/多节点）：LPUSH 到 {@code delivery:node:{gatewayNode} Redis LIST}</li>
 *   <li>直接 Dubbo 实现（all-in-one/单节点）：直接调用本地 {@code OnlineDispatcher}</li>
 * </ul>
 *
 * @author xxxcrel
 */
public interface NodeDeliveryService {

    /**
     * 向指定网关节点投递消息。
     *
     * @param gatewayNode 目标 postoffice 节点标识（来自 RouteSnapshot.gatewayNode）
     * @param req         投递请求，包含 userId、payload、可选 connectionIds
     * @return true 表示已成功入队，false 表示入队失败（触发方应走离线推送兜底）
     */
    boolean deliver(String gatewayNode, DispatchMessageReq req);
}
