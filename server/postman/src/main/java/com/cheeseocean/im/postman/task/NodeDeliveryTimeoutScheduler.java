package com.cheeseocean.im.postman.task;

import com.cheeseocean.im.postman.delivery.OfflinePushCompensationService;
import com.cheeseocean.im.postman.state.NodeDeliveryPendingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 扫描投递 attempt 超时索引。
 *
 * <p>多副本可并发扫描；Lua 状态迁移与下游推送幂等保证重复发现不会造成重复厂商推送。</p>
 */
@Component
@ConditionalOnBean(NodeDeliveryPendingStore.class)
public class NodeDeliveryTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(NodeDeliveryTimeoutScheduler.class);
    private static final int SHARD_COUNT = 64;

    private final OfflinePushCompensationService compensationService;
    private final int batchSize;

    public NodeDeliveryTimeoutScheduler(
            OfflinePushCompensationService compensationService,
            @Value("${cheeseim.delivery.outcome.timeout-scan-batch:100}") int batchSize) {
        this.compensationService = compensationService;
        this.batchSize = Math.max(1, batchSize);
    }

    @Scheduled(fixedDelayString = "${cheeseim.delivery.outcome.timeout-scan-interval-ms:1000}")
    public void compensateExpiredAttempts() {
        long now = System.currentTimeMillis();
        for (int shard = 0; shard < SHARD_COUNT; shard++) {
            for (var attempt : compensationService.findDue(shard, now, batchSize)) {
                try {
                    compensationService.expire(attempt, now);
                } catch (RuntimeException exception) {
                    // 单个 attempt 的序列化或 broker 故障不能中断其它分片的超时补偿。
                    log.warn("节点投递超时补偿失败，将按索引重试: attemptId={}", attempt.id(), exception);
                }
            }
        }
    }
}
