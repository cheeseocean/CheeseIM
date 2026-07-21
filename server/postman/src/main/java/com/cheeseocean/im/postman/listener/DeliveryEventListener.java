package com.cheeseocean.im.postman.listener;

import com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageReq;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageResp;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchPayload;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchResult;
import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.enums.OfflinePushTriggerReason;
import com.cheeseocean.im.common.api.route.OnlineRouteQueryService;
import com.cheeseocean.im.common.api.rpc.NodeDeliveryService;
import com.cheeseocean.im.common.api.rpc.OnlineDispatcher;
import com.cheeseocean.im.common.core.constants.TopicNames;
import com.cheeseocean.im.common.api.dto.route.RouteSnapshot;
import com.cheeseocean.im.common.core.queue.annotation.QueueListener;
import com.cheeseocean.im.common.core.logging.CommonLoggers;
import com.cheeseocean.im.postman.delivery.OfflinePushCompensationService;
import com.cheeseocean.im.postman.delivery.OfflinePushEventFactory;
import com.cheeseocean.im.postman.sender.OfflinePushEventProducer;
import org.slf4j.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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
    private final NodeDeliveryService nodeDeliveryService;
    private final OfflinePushCompensationService compensationService;
    private final OfflinePushEventFactory offlinePushEventFactory;
    private final long outcomeDeadlineMillis;

    @Autowired
    public DeliveryEventListener(OnlineRouteQueryService onlineRouteQueryService,
                                 OnlineDispatcher onlineDispatcher,
                                 OfflinePushEventProducer offlinePushProducer,
                                 ObjectProvider<NodeDeliveryService> nodeDeliveryServiceProvider,
                                 ObjectProvider<OfflinePushCompensationService> compensationServiceProvider,
                                 OfflinePushEventFactory offlinePushEventFactory,
                                 @Value("${cheeseim.delivery.outcome.deadline-ms:90000}")
                                 long outcomeDeadlineMillis) {
        this.onlineRouteQueryService = onlineRouteQueryService;
        this.onlineDispatcher = onlineDispatcher;
        this.offlinePushProducer = offlinePushProducer;
        this.nodeDeliveryService = nodeDeliveryServiceProvider.getIfAvailable();
        this.compensationService = compensationServiceProvider.getIfAvailable();
        this.offlinePushEventFactory = offlinePushEventFactory;
        // 必须覆盖 postoffice 60 秒 processing lease，避免租约恢复前就误判用户离线。
        this.outcomeDeadlineMillis = Math.max(75_000L, outcomeDeadlineMillis);
    }

    /**
     * 保留给不装配 Redis 补偿器的轻量测试与单机嵌入场景。
     */
    DeliveryEventListener(OnlineRouteQueryService onlineRouteQueryService,
                          OnlineDispatcher onlineDispatcher,
                          OfflinePushEventProducer offlinePushProducer,
                          ObjectProvider<NodeDeliveryService> nodeDeliveryServiceProvider) {
        this.onlineRouteQueryService = onlineRouteQueryService;
        this.onlineDispatcher = onlineDispatcher;
        this.offlinePushProducer = offlinePushProducer;
        this.nodeDeliveryService = nodeDeliveryServiceProvider.getIfAvailable();
        this.compensationService = null;
        this.offlinePushEventFactory = new OfflinePushEventFactory();
        this.outcomeDeadlineMillis = 90_000L;
    }

    @QueueListener(topic = TopicNames.DELIVERY, group = "push-delivery")
    public void onMessage(Message message) {
        try {
            handle(message);
        } catch (RuntimeException exception) {
            // 路由、节点队列或离线事件发布失败均属于可重试故障，必须抛回队列容器，
            // 不能用日志吞掉后让 Kafka/Chronicle 将本次消费视为成功。
            log.error("投递事件处理失败，等待队列重试，serverMsgId={}, receiverId={}",
                    message == null ? null : message.getServerMsgId(),
                    message == null ? null : message.getReceiverId(), exception);
            throw exception;
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
            emitOfflinePushIfNeeded(userId, message, OfflinePushTriggerReason.ROUTE_ABSENT);
            return;
        }

        // 按 gatewayNode 分组（空 node 归到 "" 组，后续走 Dubbo 降级）
        Map<String, List<RouteSnapshot>> routesByNode = routes.stream()
                .collect(Collectors.groupingBy(r ->
                        r.getGatewayNode() != null && !r.getGatewayNode().isBlank()
                                ? r.getGatewayNode() : ""));

        boolean anyDeliveredOrLegacyAccepted = false;
        List<Map.Entry<String, List<RouteSnapshot>>> outcomeCapableNodes = new ArrayList<>();
        for (Map.Entry<String, List<RouteSnapshot>> entry : routesByNode.entrySet()) {
            String gatewayNode = entry.getKey();
            DispatchMessageReq req = buildDispatchReq(userId, entry.getValue(), message);

            if (!gatewayNode.isEmpty() && nodeDeliveryService != null) {
                if (entry.getValue().stream().allMatch(RouteSnapshot::supportsDeliveryOutcomeV1)) {
                    outcomeCapableNodes.add(entry);
                } else {
                    // 滚动升级兼容：旧路由不会产生 outcome，暂时保留“成功入队即视为在线”的旧语义。
                    anyDeliveredOrLegacyAccepted |= nodeDeliveryService.deliver(gatewayNode, req);
                }
            } else {
                // 降级：直接 Dubbo（all-in-one / gatewayNode 为空的历史数据 / NodeDeliveryService 不可用）
                DispatchMessageResp resp = onlineDispatcher.dispatchMessage(req);
                anyDeliveredOrLegacyAccepted |= hasSuccessfulDispatch(resp);
            }
        }

        if (outcomeCapableNodes.isEmpty()) {
            if (!anyDeliveredOrLegacyAccepted) {
                emitOfflinePushIfNeeded(userId, message, OfflinePushTriggerReason.NODE_DELIVERY_FAILED);
            }
            return;
        }

        boolean compensationRequired = !anyDeliveredOrLegacyAccepted
                && needsOfflinePush(message)
                && compensationService != null;
        String deliveryId = message.getServerMsgId();
        if (compensationRequired) {
            List<String> expectedNodes = outcomeCapableNodes.stream().map(Map.Entry::getKey).sorted().toList();
            var registration = compensationService.register(
                    deliveryId,
                    userId,
                    expectedNodes,
                    offlinePushEventFactory.create(userId, message, OfflinePushTriggerReason.NODE_DELIVERY_FAILED),
                    System.currentTimeMillis() + outcomeDeadlineMillis);
            if (registration == com.cheeseocean.im.postman.state.NodeDeliveryPendingStore.Registration.DELIVERED
                    || registration == com.cheeseocean.im.postman.state.NodeDeliveryPendingStore.Registration.PUBLISHING
                    || registration == com.cheeseocean.im.postman.state.NodeDeliveryPendingStore.Registration.PUBLISHED) {
                return;
            }
            if (registration
                    == com.cheeseocean.im.postman.state.NodeDeliveryPendingStore.Registration.OFFLINE_READY) {
                compensationService.publishIfReady(
                        deliveryId, userId, OfflinePushTriggerReason.NODE_DELIVERY_FAILED);
                return;
            }
        }

        boolean anyAccepted = anyDeliveredOrLegacyAccepted;
        for (Map.Entry<String, List<RouteSnapshot>> entry : outcomeCapableNodes) {
            boolean accepted = nodeDeliveryService.deliver(
                    entry.getKey(), buildDispatchReq(userId, entry.getValue(), message));
            anyAccepted |= accepted;
            if (!accepted && compensationRequired) {
                compensationService.record(
                        deliveryId,
                        userId,
                        entry.getKey(),
                        false,
                        OfflinePushTriggerReason.NODE_DELIVERY_FAILED);
            }
        }

        if (!anyAccepted && !compensationRequired) {
            emitOfflinePushIfNeeded(userId, message, OfflinePushTriggerReason.NODE_DELIVERY_FAILED);
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
        req.setConnectionIds(nodeRoutes.stream()
                .map(RouteSnapshot::getConnectionId)
                .filter(connectionId -> connectionId != null && !connectionId.isBlank())
                .distinct()
                .toList());
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

    private void emitOfflinePushIfNeeded(String userId,
                                         Message message,
                                         OfflinePushTriggerReason reason) {
        if (!needsOfflinePush(message)) {
            return;
        }
        // P0-6 修复：通过 QueueAdapter 而非直连 KafkaTemplate，使 OFFLINE_PUSH 在 cheeseim.queue.type=chronicle
        // 的单机联调模式下同样能投到 Chronicle 队列被 OfflinePushEventListener 消费。
        offlinePushProducer.publish(userId, offlinePushEventFactory.create(userId, message, reason));
    }

    private boolean needsOfflinePush(Message message) {
        return message.getOptions() != null
                && Boolean.TRUE.equals(message.getOptions().getNeedOfflinePush());
    }

    private DispatchPayload toDispatchPayload(Message message) {
        DispatchPayload payload = new DispatchPayload();
        payload.setMsg(message);
        payload.setDeliveryId(message.getServerMsgId());
        return payload;
    }
}
