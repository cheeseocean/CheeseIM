package com.cheeseocean.im.postoffice.delivery;

import com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageReq;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageResp;
import com.cheeseocean.im.common.api.dto.route.NodeQueueMessage;
import com.cheeseocean.im.common.api.dto.user.KickoffCommand;
import com.cheeseocean.im.common.api.enums.NodeQueueMessageType;
import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.common.core.logging.CommonLoggers;
import com.cheeseocean.im.postoffice.api.OnlineDispatcherImpl;
import com.cheeseocean.im.postoffice.config.NodeIdentityProvider;
import com.cheeseocean.im.postoffice.connection.ConnectionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 节点投递队列消费者：后台 daemon 线程通过 BRPOP 消费本节点的投递队列，
 * 将消息反序列化后委托给 {@link OnlineDispatcherImpl} 本地投递。
 *
 * <h3>设计要点</h3>
 * <ul>
 *   <li>使用 BRPOP（阻塞式等待）而非轮询，空闲时零 CPU 开销，消息到达时即时唤醒</li>
 *   <li>单线程消费——在线投递的本质是从 Redis 搬到 Netty channel，
 *       瓶颈在 I/O 而非 CPU，单线程足够；多线程会增加 ConnectionManager 锁竞争</li>
 *   <li>daemon 线程：JVM 关闭时自动终止，不阻止进程退出</li>
 *   <li>异常安全：任何异常（Redis 断开、envelope 反序列化失败、dispatch 抛出）
 *       均被 catch 并 log，不中断消费循环</li>
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
    private static final Duration BRPOP_TIMEOUT = Duration.ofSeconds(30);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final OnlineDispatcherImpl onlineDispatcher;
    private final ConnectionManager connectionManager;
    private final String queueKey;
    private final Thread pollerThread;
    private volatile boolean running = true;

    public NodeDeliveryPoller(StringRedisTemplate redisTemplate,
                              ObjectMapper objectMapper,
                              OnlineDispatcherImpl onlineDispatcher,
                              ConnectionManager connectionManager,
                              NodeIdentityProvider nodeIdentityProvider) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.onlineDispatcher = onlineDispatcher;
        this.connectionManager = connectionManager;
        this.queueKey = RedisKeys.deliveryNodeQueue(nodeIdentityProvider.getNodeId());
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
                String json = redisTemplate.opsForList().rightPop(queueKey, BRPOP_TIMEOUT);
                if (json != null && !json.isEmpty()) {
                    dispatchLocally(json);
                }
            } catch (Exception e) {
                log.error("NodeDeliveryPoller error, queueKey={}", queueKey, e);
            }
        }
        log.info("NodeDeliveryPoller poll loop stopped, queueKey={}", queueKey);
    }

    /**
     * 将 JSON 反序列化并委托给本地 OnlineDispatcherImpl 投递。
     *
     * <p>直接调用 {@link OnlineDispatcherImpl#dispatchMessage(DispatchMessageReq)}，
     * 复用已有的去重、编解码和 Channel 写入逻辑。
     */
    private void dispatchLocally(String json) {
        try {
            NodeQueueMessage message = objectMapper.readValue(json, NodeQueueMessage.class);
            NodeQueueMessageType type = NodeQueueMessageType.fromCode(message.getType());
            if (type == NodeQueueMessageType.KICKOFF) {
                executeKickoff(message.getPayload());
                return;
            }
            if (type == NodeQueueMessageType.DELIVERY) {
                dispatchMessage(message.getPayload());
                return;
            }
            log.warn("NodeDeliveryPoller: unsupported queue message type={}, queueKey={}", message.getType(), queueKey);
        } catch (Exception e) {
            log.error("NodeDeliveryPoller: failed to deserialize or dispatch envelope, queueKey={}", queueKey, e);
        }
    }

    private void dispatchMessage(String json) throws Exception {
        DispatchMessageReq req = objectMapper.readValue(json, DispatchMessageReq.class);
        DispatchMessageResp resp = onlineDispatcher.dispatchMessage(req);
        if (resp == null || resp.getResults() == null || resp.getResults().isEmpty()) {
            log.debug("NodeDeliveryPoller: dispatch returned empty for userId={}", req.getUserId());
        }
    }

    private void executeKickoff(String json) throws Exception {
        KickoffCommand command = objectMapper.readValue(json, KickoffCommand.class);
        if (command.getDeviceId() != null && !command.getDeviceId().isBlank()
                && command.getUserId() != null && !command.getUserId().isBlank()) {
            connectionManager.kickDeviceConnections(command.getUserId(), command.getDeviceId(), command.getReason());
        } else if (command.getSessionId() != null && !command.getSessionId().isBlank()) {
            connectionManager.kickSessionConnections(command.getSessionId(), command.getReason());
        } else if (command.getUserId() != null && !command.getUserId().isBlank()) {
            connectionManager.kickUserConnections(command.getUserId(), command.getReason());
        }
    }
}
