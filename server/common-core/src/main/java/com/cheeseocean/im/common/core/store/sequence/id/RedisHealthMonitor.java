package com.cheeseocean.im.common.core.store.sequence.id;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Redis 健康监测器
 * <p>
 * 后台周期性探活 Redis，维护可用性标志；
 * 业务调用失败时也可主动调用 {@link #markUnavailable()} 触发即时降级。
 * Redis 恢复后自动切换回主路径。
 */
class RedisHealthMonitor implements HealthMonitor {

    private static final Logger log = LoggerFactory.getLogger(RedisHealthMonitor.class);

    /** 探活用的 key，不携带任何业务语义 */
    private static final String PROBE_KEY = "__seqgen_health_probe__";

    private final StringRedisTemplate redisTemplate;

    /** 当前可用性标志，true = Redis 可用 */
    private final AtomicBoolean available = new AtomicBoolean(true);

    private final ScheduledExecutorService scheduler;

    RedisHealthMonitor(StringRedisTemplate redisTemplate, Duration checkInterval, Duration pingTimeout) {
        this.redisTemplate = redisTemplate;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "seqgen-redis-health");
            t.setDaemon(true);
            return t;
        });
        // 首次检测延迟与周期相同，避免启动时立即探活
        long intervalMs = checkInterval.toMillis();
        scheduler.scheduleWithFixedDelay(this::probe, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean isAvailable() {
        return available.get();
    }

    @Override
    public void markUnavailable() {
        if (available.compareAndSet(true, false)) {
            log.warn("[SeqIdGen] Redis 标记为不可用，号段申请降级至 RocksDB");
        }
    }

    @Override
    public void shutdown() {
        scheduler.shutdownNow();
    }

    // ──────────────────────────── 私有方法 ────────────────────────────

    /**
     * 后台探活：尝试访问 Redis，根据结果更新可用性标志
     */
    private void probe() {
        try {
            // 使用 hasKey 而非 PING，更贴近实际操作场景
            redisTemplate.hasKey(PROBE_KEY);
            if (available.compareAndSet(false, true)) {
                log.info("[SeqIdGen] Redis 恢复可用，切换回 Redis 号段分配");
            }
        } catch (Exception e) {
            if (available.compareAndSet(true, false)) {
                log.warn("[SeqIdGen] Redis 探活失败，维持降级状态: {}", e.getMessage());
            }
        }
    }
}
