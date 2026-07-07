package com.cheeseocean.im.postman.listener;

import com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageReq;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageResp;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchPayload;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchResult;
import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.enums.ChatType;
import com.cheeseocean.im.common.api.event.OfflinePushEvent;
import com.cheeseocean.im.common.api.route.OnlineRouteQueryService;
import com.cheeseocean.im.common.api.rpc.NodeDeliveryService;
import com.cheeseocean.im.common.api.rpc.OnlineDispatcher;
import com.cheeseocean.im.common.core.constants.TopicNames;
import com.cheeseocean.im.common.api.dto.route.RouteSnapshot;
import com.cheeseocean.im.common.core.queue.annotation.QueueListener;
import com.cheeseocean.im.common.core.logging.CommonLoggers;
import com.cheeseocean.im.common.core.util.ConversationIdUtil;
import com.cheeseocean.im.postman.sender.OfflinePushEventProducer;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class DeliveryEventListener {
    private static final Logger       log = CommonLoggers.POSTMAN;
    private final OnlineRouteQueryService onlineRouteQueryService;
    private final OnlineDispatcher        onlineDispatcher;
    private final OfflinePushEventProducer offlinePushProducer;
    @Autowired(required = false)
    private NodeDeliveryService nodeDeliveryService;

    public DeliveryEventListener(OnlineRouteQueryService onlineRouteQueryService,
                                 OnlineDispatcher onlineDispatcher,
                                 OfflinePushEventProducer offlinePushProducer) {
        this.onlineRouteQueryService = onlineRouteQueryService;
        this.onlineDispatcher = onlineDispatcher;
        this.offlinePushProducer = offlinePushProducer;
    }

    @QueueListener(topic = TopicNames.DELIVERY, group = "push-delivery")
    public void onMessage(Message message) {
        try {
            handle(message);
        } catch (Exception e) {
            log.error("Failed to handle delivery message: {}", message, e);
        }
    }

    void handle(Message message) {
        if (message == null) {
            return;
        }
        for (String userId : resolveTargets(message)) {
            deliverToUser(userId, message);
        }
    }

    private List<String> resolveTargets(Message message) {
        // 写扩散群消息已在 postmaster 端拆解为 per-member DeliveryEvent（chatType=GROUP, receiverId=memberId）；
        // 读扩散群消息（SUPER_GROUP）不会进入 DELIVERY 队列，因此本方法可直接按 receiverId 投递，
        // 不再按 chatType 跳过群投递。
        if (message.getReceiverId() == null || message.getReceiverId().isBlank()) {
            return List.of();
        }
        return List.of(message.getReceiverId());
    }

    /**
     * P0-1 修复：按 gatewayNode 分组路由，将投递请求发送到持有目标用户连接的 postoffice 节点。
     *
     * <p>投递策略：
     * <ol>
     *   <li>查路由表获取用户在线设备及所在节点</li>
     *   <li>如果路由为空 → 用户离线，走离线推送</li>
     *   <li>按 gatewayNode 分组路由</li>
     *   <li>对每个节点：
     *     <ul>
     *       <li>如果 NodeDeliveryService 可用且 gatewayNode 非空 → LPUSH 到目标节点的 Redis LIST</li>
     *       <li>否则降级为直接 Dubbo 调用（all-in-one 模式或 gatewayNode 为空的历史数据）</li>
     *     </ul>
     *   </li>
     *   <li>有任一投递成功 → 不推送离线；全部失败 → 走离线推送兜底</li>
     * </ol>
     */
    private void deliverToUser(String userId, Message message) {
        List<RouteSnapshot> routes = onlineRouteQueryService.findByUser(userId);
        if (routes == null || routes.isEmpty()) {
            emitOfflinePushIfNeeded(userId, message);
            return;
        }

        // 按 gatewayNode 分组（空 node 归到 "" 组，后续走 Dubbo 降级）
        Map<String, List<RouteSnapshot>> routesByNode = routes.stream()
                .collect(Collectors.groupingBy(r ->
                        r.getGatewayNode() != null && !r.getGatewayNode().isBlank()
                                ? r.getGatewayNode() : ""));

        boolean anyDelivered = false;
        for (Map.Entry<String, List<RouteSnapshot>> entry : routesByNode.entrySet()) {
            String gatewayNode = entry.getKey();
            DispatchMessageReq req = buildDispatchReq(userId, entry.getValue(), message);

            if (!gatewayNode.isEmpty() && nodeDeliveryService != null) {
                // P0-1: 按节点路由投递（Redis LIST）
                anyDelivered |= nodeDeliveryService.deliver(gatewayNode, req);
            } else {
                // 降级：直接 Dubbo（all-in-one / gatewayNode 为空的历史数据 / NodeDeliveryService 不可用）
                DispatchMessageResp resp = onlineDispatcher.dispatchMessage(req);
                anyDelivered |= hasSuccessfulDispatch(resp);
            }
        }

        if (!anyDelivered) {
            // 极端兜底：所有节点投递均失败，走离线推送
            emitOfflinePushIfNeeded(userId, message);
        }
    }

    /**
     * 为同一节点上的路由列表构建投递请求。
     *
     * <p>如果同一节点上有多个设备在线，将 connectionIds 全部传入，
     * OnlineDispatcherImpl 会逐条投递并返回每个设备的结果。
     */
    private DispatchMessageReq buildDispatchReq(String userId, List<RouteSnapshot> nodeRoutes, Message message) {
        DispatchMessageReq req = new DispatchMessageReq();
        req.setUserId(userId);
        req.setPayload(toDispatchPayload(message));
        // 不设置 connectionIds——让 OnlineDispatcherImpl 通过 userId 查本地连接
        // （节点路由已保证投递到正确节点，按 userId 全量投递即可）
        return req;
    }

    private boolean hasSuccessfulDispatch(DispatchMessageResp resp) {
        if (resp == null || resp.getResults() == null || resp.getResults().isEmpty()) {
            return false;
        }
        for (DispatchResult result : resp.getResults()) {
            if (result.isSuccess()) {
                return true;
            }
        }
        return false;
    }

    private void emitOfflinePushIfNeeded(String userId, Message message) {
        if (message.getOptions() == null || !Boolean.TRUE.equals(message.getOptions().getNeedOfflinePush())) {
            return;
        }
        // P0-6 修复：通过 QueueAdapter 而非直连 KafkaTemplate，使 OFFLINE_PUSH 在 cheeseim.queue.type=chronicle
        // 的单机联调模式下同样能投到 Chronicle 队列被 OfflinePushEventListener 消费。
        offlinePushProducer.publish(userId, toOfflinePushEvent(userId, message));
    }

    private DispatchPayload toDispatchPayload(Message message) {
        DispatchPayload payload = new DispatchPayload();
        payload.setMsg(message);
        return payload;
    }

    private OfflinePushEvent toOfflinePushEvent(String userId, Message message) {
        OfflinePushEvent event = new OfflinePushEvent();
        event.setUserId(userId);
        event.setConversationId(ConversationIdUtil.buildConversationId(message));
        event.setSeq(message.getSeq());
        event.setServerMsgId(message.getServerMsgId());
        event.setSenderId(message.getSenderId());
        event.setSessionType(message.getChatType() == null ? null : message.getChatType().getCode());
        event.setContentType(message.getContentType() == null ? null : message.getContentType().getCode());
        event.setNotification(message.getOptions() != null && Boolean.TRUE.equals(message.getOptions().getNotification()));
        event.setTitle(message.getSenderId());
        event.setContent(message.getContent() == null ? null : new String(message.getContent(), StandardCharsets.UTF_8));
        event.setAttributes(message.getAttributes());
        return event;
    }
}
