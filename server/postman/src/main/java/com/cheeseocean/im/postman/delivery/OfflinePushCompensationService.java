package com.cheeseocean.im.postman.delivery;

import com.cheeseocean.im.common.api.enums.OfflinePushTriggerReason;
import com.cheeseocean.im.common.api.event.OfflinePushEvent;
import com.cheeseocean.im.postman.sender.OfflinePushEventProducer;
import com.cheeseocean.im.postman.state.NodeDeliveryPendingStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 在线投递失败到离线推送之间的可靠补偿协调器。
 *
 * <p>先发布队列事件，broker 返回成功后再把 attempt 标成 PUBLISHED；中间崩溃最多造成
 * OFFLINE_PUSH 重复，后续 PushStateStore 会继续以 serverMsgId + userId 抑制厂商重复推送。</p>
 */
@Component
@ConditionalOnBean(NodeDeliveryPendingStore.class)
public class OfflinePushCompensationService {

    private static final long PUBLISH_LEASE_MILLIS = 30_000L;

    private final NodeDeliveryPendingStore pendingStore;
    private final OfflinePushEventProducer offlinePushProducer;

    public OfflinePushCompensationService(NodeDeliveryPendingStore pendingStore,
                                          OfflinePushEventProducer offlinePushProducer) {
        this.pendingStore = pendingStore;
        this.offlinePushProducer = offlinePushProducer;
    }

    public NodeDeliveryPendingStore.Registration register(String deliveryId,
                                                          String userId,
                                                          List<String> expectedNodes,
                                                          OfflinePushEvent offlineEvent,
                                                          long deadlineMillis) {
        return pendingStore.register(
                pendingStore.identity(deliveryId, userId), expectedNodes, offlineEvent, deadlineMillis);
    }

    public void record(String deliveryId,
                       String userId,
                       String gatewayNode,
                       boolean delivered,
                       OfflinePushTriggerReason failureReason) {
        var attempt = pendingStore.identity(deliveryId, userId);
        var outcome = pendingStore.recordNodeOutcome(attempt, gatewayNode, delivered);
        if (outcome == NodeDeliveryPendingStore.Outcome.OFFLINE_READY) {
            publishReady(attempt, failureReason);
        }
    }

    public void expire(NodeDeliveryPendingStore.AttemptRef attempt, long nowMillis) {
        if (pendingStore.expire(attempt, nowMillis) == NodeDeliveryPendingStore.Outcome.OFFLINE_READY) {
            publishReady(attempt, OfflinePushTriggerReason.NODE_DELIVERY_TIMEOUT);
        }
    }

    public List<NodeDeliveryPendingStore.AttemptRef> findDue(int shard, long nowMillis, int limit) {
        return pendingStore.findDue(shard, nowMillis, limit);
    }

    public void publishIfReady(String deliveryId, String userId, OfflinePushTriggerReason reason) {
        publishReady(pendingStore.identity(deliveryId, userId), reason);
    }

    private void publishReady(NodeDeliveryPendingStore.AttemptRef attempt,
                              OfflinePushTriggerReason reason) {
        if (!pendingStore.claimOfflinePublish(
                attempt, System.currentTimeMillis(), PUBLISH_LEASE_MILLIS)) {
            return;
        }
        try {
            OfflinePushEvent event = pendingStore.findOfflineEvent(attempt)
                    .orElseThrow(() -> new IllegalStateException("Offline compensation event is missing"));
            event.setTriggerReason(reason);
            offlinePushProducer.publish(event.getUserId(), event);
            if (!pendingStore.markOfflinePublished(attempt)) {
                throw new IllegalStateException("Offline compensation publish claim disappeared");
            }
        } catch (RuntimeException exception) {
            try {
                pendingStore.releaseOfflinePublish(attempt, System.currentTimeMillis() + 1_000L);
            } catch (RuntimeException releaseFailure) {
                exception.addSuppressed(releaseFailure);
            }
            throw exception;
        }
    }
}
