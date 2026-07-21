package com.cheeseocean.im.postoffice.login;

import com.cheeseocean.im.postoffice.config.ServerProperties;
import com.cheeseocean.im.postoffice.connection.ConnectionManager;
import com.cheeseocean.im.postoffice.connection.UserConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 合并客户端心跳产生的全局登录 lease 续租。
 *
 * <p>续租返回 FENCED/MISSING 时立即精确关闭本地旧连接，使 Redis 逻辑 fencing 收敛到物理 channel。</p>
 */
@Component
public class LoginLeaseHeartbeatBuffer {

    private static final Logger log = LoggerFactory.getLogger(LoginLeaseHeartbeatBuffer.class);

    private final LoginLeaseStore store;
    private final ConnectionManager connectionManager;
    private final boolean enforce;
    private final long renewIntervalMs;
    private final int batchSize;
    private final ConcurrentHashMap<String, PendingRenewal> pending = new ConcurrentHashMap<>();
    private final AtomicLong nextFullRenewAt;

    public LoginLeaseHeartbeatBuffer(ObjectProvider<LoginLeaseStore> storeProvider,
                                     ConnectionManager connectionManager,
                                     ServerProperties properties) {
        this.store = storeProvider.getIfAvailable();
        this.connectionManager = connectionManager;
        this.enforce = properties.getLoginLease().isEnforce();
        this.renewIntervalMs = Math.max(10_000L, properties.getLoginLease().getRenewIntervalMs());
        this.batchSize = Math.max(100, properties.getLoginLease().getRenewBatchSize());
        this.nextFullRenewAt = new AtomicLong(System.currentTimeMillis() + renewIntervalMs);
        if (enforce && store == null) {
            throw new IllegalStateException("login lease enforce requires Redis LoginLeaseStore");
        }
        if (enforce && properties.getLoginLease().getTtlMs() < renewIntervalMs * 2) {
            throw new IllegalStateException("login lease ttl must be at least twice renew interval");
        }
    }

    public void record(UserConnection connection) {
        if (!enforce || connection == null || connection.getLoginLeaseGeneration() == null
                || connection.getConnectionID() == null || connection.getUserID() == null) {
            return;
        }
        LoginLeaseRenewal renewal = new LoginLeaseRenewal(
                Objects.toString(connection.getTenantId(), "default"),
                connection.getUserID(),
                connection.getConnectionID(),
                connection.getLoginLeaseGeneration());
        pending.compute(connection.getConnectionID(), (ignored, current) -> new PendingRenewal(
                renewal,
                current == null ? System.currentTimeMillis() + renewIntervalMs : current.renewAt()));
    }

    @Scheduled(fixedDelayString = "${cheeseim.postoffice.login-lease.flush-interval-ms:1000}")
    public void renewDue() {
        if (!enforce) {
            return;
        }
        long now = System.currentTimeMillis();
        stageFullRenewalIfDue(now);
        if (pending.isEmpty()) {
            return;
        }
        List<PendingRenewal> due = new ArrayList<>(Math.min(batchSize, pending.size()));
        for (PendingRenewal item : pending.values()) {
            if (item.renewAt() <= now) {
                due.add(item);
                if (due.size() >= batchSize) {
                    break;
                }
            }
        }
        if (due.isEmpty()) {
            return;
        }
        try {
            Set<String> fenced = store.renewBatch(due.stream().map(PendingRenewal::renewal).toList());
            for (PendingRenewal item : due) {
                pending.remove(item.renewal().connectionId(), item);
            }
            for (String connectionId : fenced) {
                UserConnection connection = connectionManager.getConnection(connectionId);
                if (connection != null) {
                    connectionManager.kickConnectionById(
                            connectionId,
                            connection.getLoginLeaseGeneration(),
                            "登录租约已被新连接替换");
                }
            }
        } catch (RuntimeException exception) {
            long retryAt = System.currentTimeMillis() + 5_000L;
            for (PendingRenewal item : due) {
                pending.computeIfPresent(item.renewal().connectionId(),
                        (ignored, current) -> current == item
                                ? new PendingRenewal(current.renewal(), retryAt)
                                : current);
            }
            log.warn("批量续租登录 lease 失败，保留待刷项重试: count={}", due.size(), exception);
        }
    }

    /**
     * 服务端主动扫描本地连接，不依赖客户端主动心跳，恶意静默连接也不能绕过 lease fencing。
     */
    private void stageFullRenewalIfDue(long now) {
        long expected = nextFullRenewAt.get();
        if (now < expected || !nextFullRenewAt.compareAndSet(expected, now + renewIntervalMs)) {
            return;
        }
        for (UserConnection connection : connectionManager.snapshotConnections()) {
            if (connection.isAuthenticated() && connection.getLoginLeaseGeneration() != null) {
                LoginLeaseRenewal renewal = new LoginLeaseRenewal(
                        Objects.toString(connection.getTenantId(), "default"),
                        connection.getUserID(),
                        connection.getConnectionID(),
                        connection.getLoginLeaseGeneration());
                pending.put(connection.getConnectionID(), new PendingRenewal(renewal, now));
            }
        }
    }

    private record PendingRenewal(LoginLeaseRenewal renewal, long renewAt) {
    }
}
