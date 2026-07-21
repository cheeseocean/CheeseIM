package com.cheeseocean.im.postoffice.service;

import com.cheeseocean.im.postoffice.config.ServerProperties;
import com.cheeseocean.im.postoffice.connection.ConnectionManager;
import com.cheeseocean.im.postoffice.connection.UserConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 节点本地路由心跳合并缓冲。
 *
 * <p>客户端心跳只覆盖同一 connection 的最新时间；到达持久化间隔后批量写 Redis。
 * 写失败保留待刷项，下次调度重试。</p>
 */
@Component
@EnableScheduling
public class RouteHeartbeatBuffer {

    private static final Logger log = LoggerFactory.getLogger(RouteHeartbeatBuffer.class);

    private final OnlineRouteHeartbeatWriter writer;
    private final long persistIntervalMillis;
    private final int flushBatchSize;
    private final ConcurrentHashMap<String, PendingHeartbeat> pending = new ConcurrentHashMap<>();

    public RouteHeartbeatBuffer(OnlineRouteHeartbeatWriter writer, ServerProperties properties) {
        this.writer = writer;
        this.persistIntervalMillis = Math.max(
                30_000L, properties.getRouteHeartbeat().getPersistIntervalMs());
        this.flushBatchSize = Math.max(100, properties.getRouteHeartbeat().getFlushBatchSize());
    }

    public void record(UserConnection connection) {
        String deviceId = ConnectionManager.routeDeviceId(connection);
        if (connection == null || connection.getUserID() == null || deviceId == null
                || connection.getConnectionID() == null) {
            return;
        }
        String identity = connection.getConnectionID();
        pending.compute(identity, (ignored, current) -> new PendingHeartbeat(
                new RouteHeartbeat(
                        connection.getUserID(),
                        deviceId,
                        connection.getSessionId(),
                        connection.getConnectionID(),
                        connection.getLastActiveTime()),
                current == null
                        ? System.currentTimeMillis() + persistIntervalMillis
                        : current.flushAt()));
    }

    @Scheduled(fixedDelayString = "${cheeseim.postoffice.route-heartbeat.flush-interval-ms:1000}")
    public void flushDue() {
        long now = System.currentTimeMillis();
        List<PendingHeartbeat> due = new ArrayList<>(Math.min(flushBatchSize, pending.size()));
        for (PendingHeartbeat heartbeat : pending.values()) {
            if (heartbeat.flushAt() <= now) {
                due.add(heartbeat);
                if (due.size() >= flushBatchSize) {
                    break;
                }
            }
        }
        if (due.isEmpty()) {
            return;
        }
        try {
            writer.refreshBatch(due.stream().map(PendingHeartbeat::heartbeat).toList());
            for (PendingHeartbeat heartbeat : due) {
                pending.remove(heartbeat.heartbeat().connectionId(), heartbeat);
            }
        } catch (RuntimeException exception) {
            long retryAt = System.currentTimeMillis() + 5_000L;
            for (PendingHeartbeat heartbeat : due) {
                pending.computeIfPresent(
                        heartbeat.heartbeat().connectionId(),
                        (ignored, current) -> current == heartbeat
                                ? new PendingHeartbeat(current.heartbeat(), retryAt)
                                : current);
            }
            log.warn("批量刷新在线路由心跳失败，将保留本地待刷项重试: count={}", due.size(), exception);
        }
    }

    private record PendingHeartbeat(RouteHeartbeat heartbeat, long flushAt) {
    }
}
