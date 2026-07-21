package com.cheeseocean.im.postoffice.delivery;

import com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageReq;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageResp;
import com.cheeseocean.im.common.api.dto.route.NodeQueueMessage;
import com.cheeseocean.im.common.api.dto.user.KickoffCommand;
import com.cheeseocean.im.common.api.enums.NodeQueueMessageType;
import com.cheeseocean.im.common.api.enums.DispatchResultCode;
import com.cheeseocean.im.common.api.enums.NodeDeliveryOutcomeCode;
import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.common.core.logging.CommonLoggers;
import com.cheeseocean.im.common.core.queue.NodeQueueRedisScripts;
import com.cheeseocean.im.common.core.metrics.ImMetrics;
import com.cheeseocean.im.postoffice.api.OnlineDispatcherImpl;
import com.cheeseocean.im.postoffice.config.NodeIdentityProvider;
import com.cheeseocean.im.postoffice.connection.ConnectionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 节点投递队列消费者：后台 daemon 线程通过 Lua 将本节点消息原子领取到带超时租约的 processing，
 * 将消息反序列化后委托给 {@link OnlineDispatcherImpl} 本地投递。
 *
 * <h3>设计要点</h3>
 * <ul>
 *   <li>执行成功后 ACK claim；失败重试，同 node-id 的实例周期回收过期租约</li>
 *   <li>单线程消费——在线投递的本质是从 Redis 搬到 Netty channel，
 *       瓶颈在 I/O 而非 CPU，单线程足够；多线程会增加 ConnectionManager 锁竞争</li>
 *   <li>daemon 线程：JVM 关闭时自动终止，不阻止进程退出</li>
 *   <li>异常安全：任何异常（Redis 断开、envelope 反序列化失败、dispatch 抛出）
 *       均被 catch 并 log，不中断消费循环；基础设施失败保留 claim 等待租约恢复</li>
 * </ul>
 *
 * <h3>all-in-one 兼容</h3>
 * <p>单 JVM 下 postman LPUSH 和 postoffice BRPOP 共享同一个 Redis LIST，
 * 投递路径与多节点一致，无需特殊处理。
 *
 * <p>实现 P0-1 跨节点在线投递修复（ASSESSMENT §3.1 P0-1 / §5 P0 §1）。
 *
 * @author xxxcrel
 */
@Component
public class NodeDeliveryPoller {

    private static final Logger log = CommonLoggers.POSTOFFICE;
    private static final int MAX_RETRY_COUNT = 5;
    private static final long EMPTY_POLL_INTERVAL_MILLIS = 100L;
    private static final long FAILURE_BACKOFF_MILLIS = 500L;
    private static final long RECOVERY_INTERVAL_MILLIS = 5_000L;
    private static final int MAX_RECOVERY_BATCH = 100;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final OnlineDispatcherImpl onlineDispatcher;
    private final ConnectionManager connectionManager;
    private final NodeDeliveryOutcomeProducer outcomeProducer;
    private final String queueKey;
    private final String processingQueueKey;
    private final String processingLeaseKey;
    private final String deadLetterQueueKey;
    private final String nodeId;
    private final Thread pollerThread;
    private volatile boolean running = true;
    private volatile long lastDepthSampleAt;
    private volatile long lastRecoveryAt;

    public NodeDeliveryPoller(StringRedisTemplate redisTemplate,
                              ObjectMapper objectMapper,
                              OnlineDispatcherImpl onlineDispatcher,
                              ConnectionManager connectionManager,
                              NodeIdentityProvider nodeIdentityProvider,
                              NodeDeliveryOutcomeProducer outcomeProducer) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.onlineDispatcher = onlineDispatcher;
        this.connectionManager = connectionManager;
        this.outcomeProducer = outcomeProducer;
        this.nodeId = nodeIdentityProvider.getNodeId();
        this.queueKey = RedisKeys.deliveryNodeQueue(nodeId);
        this.processingQueueKey = RedisKeys.deliveryNodeProcessingQueue(nodeId);
        this.processingLeaseKey = processingQueueKey + ":leases";
        this.deadLetterQueueKey = RedisKeys.deliveryNodeDeadLetterQueue(nodeId);
        this.pollerThread = new Thread(this::pollLoop, "node-delivery-poller");
        this.pollerThread.setDaemon(true);
    }

    @PostConstruct
    void start() {
        pollerThread.start();
        log.info("NodeDeliveryPoller started, queueKey={}", queueKey);
    }

    @PreDestroy
    void stop() {
        running = false;
        pollerThread.interrupt();
        log.info("NodeDeliveryPoller stopping, queueKey={}", queueKey);
    }

    private void pollLoop() {
        log.info("NodeDeliveryPoller poll loop started, queueKey={}", queueKey);
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                recoverExpiredMessagesIfDue();
                String claimId = UUID.randomUUID().toString();
                String json = redisTemplate.execute(NodeQueueRedisScripts.CLAIM,
                        List.of(queueKey, processingQueueKey, processingLeaseKey),
                        claimId,
                        Long.toString(System.currentTimeMillis() + NodeQueueRedisScripts.PROCESSING_LEASE_MILLIS),
                        Long.toString(NodeQueueRedisScripts.MAX_PROCESSING_SIZE),
                        Long.toString(NodeQueueRedisScripts.QUEUE_TTL_SECONDS));
                if (json != null && !json.isEmpty()) {
                    consume(claimId, json);
                } else {
                    TimeUnit.MILLISECONDS.sleep(EMPTY_POLL_INTERVAL_MILLIS);
                }
                sampleQueueDepth();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("NodeDeliveryPoller error, queueKey={}", queueKey, e);
                try {
                    TimeUnit.MILLISECONDS.sleep(FAILURE_BACKOFF_MILLIS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        log.info("NodeDeliveryPoller poll loop stopped, queueKey={}", queueKey);
    }

    private void recoverExpiredMessagesIfDue() {
        long now = System.currentTimeMillis();
        if (now - lastRecoveryAt < RECOVERY_INTERVAL_MILLIS) {
            return;
        }
        lastRecoveryAt = now;
        int count = 0;
        for (int i = 0; i < MAX_RECOVERY_BATCH; i++) {
            Long result = redisTemplate.execute(NodeQueueRedisScripts.RECOVER_EXPIRED,
                    List.of(processingQueueKey, processingLeaseKey, queueKey),
                    Long.toString(now), Long.toString(NodeQueueRedisScripts.MAX_QUEUE_SIZE),
                    Long.toString(NodeQueueRedisScripts.QUEUE_TTL_SECONDS),
                    Long.toString(now + NodeQueueRedisScripts.PROCESSING_LEASE_MILLIS));
            if (result == null || result == 0L || result == -1L) {
                break;
            }
            if (result == 1L) count++;
        }
        if (count > 0) {
            log.warn("Recovered unacknowledged node messages, queueKey={}, count={}", queueKey, count);
        }
    }

    private void consume(String claimId, String json) {
        try {
            NodeQueueMessage message = objectMapper.readValue(json, NodeQueueMessage.class);
            dispatchLocally(message);
            Long acknowledged = redisTemplate.execute(NodeQueueRedisScripts.ACK,
                    List.of(processingQueueKey, processingLeaseKey), claimId);
            if (!Long.valueOf(1L).equals(acknowledged)) {
                throw new IllegalStateException("Node processing claim disappeared before ACK");
            }
        } catch (DeliveryPendingException pending) {
            // 保留 processing claim，等待租约回收后再验证 dedup 终态，避免快速重试耗尽次数。
            log.debug("Node delivery still pending, claimId={}, queueKey={}", claimId, queueKey);
        } catch (Exception e) {
            retryOrDeadLetter(claimId, json, e);
        }
    }

    /**
     * 将 JSON 反序列化并委托给本地 OnlineDispatcherImpl 投递。
     *
     * <p>直接调用 {@link OnlineDispatcherImpl#dispatchMessage(DispatchMessageReq)}，
     * 复用已有的去重、编解码和 Channel 写入逻辑。
     */
    private void dispatchLocally(NodeQueueMessage message) throws Exception {
        NodeQueueMessageType type = NodeQueueMessageType.fromCode(message.getType());
        if (type == NodeQueueMessageType.KICKOFF) {
            executeKickoff(message.getPayload());
            return;
        }
        if (type == NodeQueueMessageType.DELIVERY) {
            dispatchMessage(message.getPayload());
            return;
        }
        throw new IllegalArgumentException("Unsupported node queue message type: " + message.getType());
    }

    private void retryOrDeadLetter(String claimId, String json, Exception cause) {
        NodeQueueMessage message;
        try {
            message = objectMapper.readValue(json, NodeQueueMessage.class);
        } catch (Exception malformed) {
            completeClaim(claimId, json, deadLetterQueueKey, true);
            ImMetrics.nodeRetry("malformed");
            log.error("Malformed node message moved to dead letter queue, queueKey={}", queueKey, malformed);
            return;
        }

        message.setRetryCount(message.getRetryCount() + 1);
        try {
            boolean dead = message.getRetryCount() >= MAX_RETRY_COUNT;
            if (dead) {
                publishFinalFailure(message, cause);
            }
            String retriedJson = objectMapper.writeValueAsString(message);
            Long result = completeClaim(claimId, retriedJson, dead ? deadLetterQueueKey : queueKey, dead);
            if (Long.valueOf(-1L).equals(result)) {
                ImMetrics.nodeRetry("ready_overflow");
                log.error("Node ready queue full; claim retained with renewed lease, queueKey={}", queueKey, cause);
            } else if (dead) {
                ImMetrics.nodeRetry("dead");
                log.error("Node message moved to bounded dead letter queue, queueKey={}, retryCount={}",
                        queueKey, message.getRetryCount(), cause);
            } else {
                ImMetrics.nodeRetry("requeued");
                log.warn("Node message requeued, queueKey={}, retryCount={}", queueKey, message.getRetryCount(), cause);
            }
        } catch (Exception infrastructureFailure) {
            ImMetrics.nodeRetry("infrastructure_error");
            log.error("Failed to move node processing claim; lease recovery will retry, queueKey={}, claimId={}",
                    queueKey, claimId, infrastructureFailure);
        }
    }

    private void sampleQueueDepth() {
        long now = System.currentTimeMillis();
        if (now - lastDepthSampleAt < 1_000L) {
            return;
        }
        lastDepthSampleAt = now;
        updateDepth("ready", queueKey);
        updateDepth("processing", processingQueueKey);
        updateDepth("dead", deadLetterQueueKey);
    }

    private void updateDepth(String state, String key) {
        Long size = state.equals("processing")
                ? redisTemplate.opsForHash().size(key)
                : redisTemplate.opsForList().size(key);
        ImMetrics.nodeQueueDepth(nodeId, state, size == null ? 0 : size);
    }

    private Long completeClaim(String claimId, String targetJson, String targetQueueKey, boolean deadLetter) {
        long capacity = deadLetter ? NodeQueueRedisScripts.MAX_DEAD_LETTER_SIZE : NodeQueueRedisScripts.MAX_QUEUE_SIZE;
        Long moved = redisTemplate.execute(NodeQueueRedisScripts.COMPLETE,
                List.of(processingQueueKey, processingLeaseKey, targetQueueKey), claimId, targetJson,
                Long.toString(capacity), Long.toString(NodeQueueRedisScripts.QUEUE_TTL_SECONDS),
                deadLetter ? "1" : "0",
                Long.toString(System.currentTimeMillis() + NodeQueueRedisScripts.PROCESSING_LEASE_MILLIS));
        if (!Long.valueOf(1L).equals(moved)) {
            if (Long.valueOf(-1L).equals(moved)) return moved;
            throw new IllegalStateException("Node processing message disappeared before atomic move");
        }
        return moved;
    }

    private void dispatchMessage(String json) throws Exception {
        DispatchMessageReq req = objectMapper.readValue(json, DispatchMessageReq.class);
        DispatchMessageResp resp = onlineDispatcher.dispatchMessage(req);
        if (resp == null || resp.getResults() == null || resp.getResults().isEmpty()) {
            throw new IllegalStateException("Local dispatch returned no result for userId=" + req.getUserId());
        }
        boolean anyDelivered = false;
        for (var result : resp.getResults()) {
            if (result == null) {
                throw new IllegalStateException("Local dispatch returned null connection result");
            }
            if (result.isSuccess()) {
                anyDelivered = true;
                continue;
            }
            DispatchResultCode code = DispatchResultCode.fromCode(result.getCode());
            if (code == DispatchResultCode.CONNECTION_NOT_FOUND) {
                // 路由已陈旧属于当前节点的终态，postman 会按用户级结果决定离线推送。
                continue;
            }
            if (code == DispatchResultCode.DELIVERY_IN_PROGRESS
                    || code == DispatchResultCode.WRITE_PENDING) {
                throw new DeliveryPendingException();
            }
            throw new IllegalStateException(
                    "Local dispatch failed, userId=" + req.getUserId() + ", code=" + result.getCode());
        }
        outcomeProducer.publish(
                nodeId,
                req,
                anyDelivered
                        ? NodeDeliveryOutcomeCode.DELIVERED
                        : NodeDeliveryOutcomeCode.NO_ACTIVE_CONNECTION,
                resp,
                null);
    }

    private void executeKickoff(String json) throws Exception {
        KickoffCommand command = objectMapper.readValue(json, KickoffCommand.class);
        if (command.getConnectionId() != null && !command.getConnectionId().isBlank()) {
            connectionManager.kickConnectionById(
                    command.getConnectionId(),
                    command.getLoginLeaseGeneration(),
                    command.getReason());
        } else if (command.getDeviceId() != null && !command.getDeviceId().isBlank()
                && command.getUserId() != null && !command.getUserId().isBlank()) {
            connectionManager.kickDeviceConnections(command.getUserId(), command.getDeviceId(), command.getReason());
        } else if (command.getSessionId() != null && !command.getSessionId().isBlank()) {
            connectionManager.kickSessionConnections(command.getSessionId(), command.getReason());
        } else if (command.getUserId() != null && !command.getUserId().isBlank()) {
            connectionManager.kickUserConnections(command.getUserId(), command.getReason());
        }
    }

    private void publishFinalFailure(NodeQueueMessage message, Exception cause) throws Exception {
        if (NodeQueueMessageType.fromCode(message.getType()) != NodeQueueMessageType.DELIVERY) {
            return;
        }
        DispatchMessageReq request = objectMapper.readValue(message.getPayload(), DispatchMessageReq.class);
        outcomeProducer.publish(
                nodeId,
                request,
                NodeDeliveryOutcomeCode.FAILED_FINAL,
                null,
                cause == null ? "node delivery retry exhausted" : cause.getClass().getSimpleName());
    }

    private static final class DeliveryPendingException extends Exception {
    }
}
