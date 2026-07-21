package com.cheeseocean.im.postman.state;

import com.cheeseocean.im.common.api.event.OfflinePushEvent;

import java.util.List;
import java.util.Optional;

/**
 * 用户级在线投递结果聚合状态。
 *
 * <p>接口隔离 Redis 细节，使监听器只表达 attempt 状态机，不依赖具体脚本。</p>
 */
public interface NodeDeliveryPendingStore {

    AttemptRef identity(String deliveryId, String userId);

    Registration register(AttemptRef attempt,
                          List<String> expectedNodes,
                          OfflinePushEvent offlineEvent,
                          long deadlineMillis);

    Outcome recordNodeOutcome(AttemptRef attempt, String gatewayNode, boolean delivered);

    Outcome expire(AttemptRef attempt, long nowMillis);

    List<AttemptRef> findDue(int shard, long nowMillis, int limit);

    Optional<OfflinePushEvent> findOfflineEvent(AttemptRef attempt);

    boolean claimOfflinePublish(AttemptRef attempt, long nowMillis, long leaseMillis);

    void releaseOfflinePublish(AttemptRef attempt, long retryAtMillis);

    boolean markOfflinePublished(AttemptRef attempt);

    record AttemptRef(String id, int shard) {
    }

    enum Registration {
        NEW, PENDING, DELIVERED, OFFLINE_READY, PUBLISHING, PUBLISHED
    }

    enum Outcome {
        MISSING, WAITING, DELIVERED, OFFLINE_READY, PUBLISHING, PUBLISHED, IGNORED
    }
}
