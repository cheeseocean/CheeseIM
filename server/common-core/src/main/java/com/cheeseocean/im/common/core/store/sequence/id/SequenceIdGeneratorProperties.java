package com.cheeseocean.im.common.core.store.sequence.id;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SequenceIdGenerator 配置属性
 * <p>
 * 前缀：{@code cheeseim.seqid}
 *
 * <pre>
 * cheeseim:
 *   seqid:
 *     default-range-size: 1000        # 每次向持久化层申请的号段大小
 *     redis-check-interval-ms: 5000   # Redis 探活周期（毫秒）
 *     redis-ping-timeout-ms: 500      # Redis 探活超时（毫秒）
 * </pre>
 */
@ConfigurationProperties(prefix = "cheeseim.seqid")
public class SequenceIdGeneratorProperties {

    /**
     * 每次向 Redis / RocksDB 申请的号段大小
     * 值越大，向持久化层申请的频率越低，吞吐量越高；但 crash 后的 ID 空洞也越大
     */
    private int defaultRangeSize = 1000;

    /**
     * Redis 健康探活间隔（毫秒）
     * Redis 故障后，以此频率尝试恢复
     */
    private long redisCheckIntervalMs = 5_000;

    /**
     * Redis 探活单次超时（毫秒）
     */
    private long redisPingTimeoutMs = 500;

    public int getDefaultRangeSize() {
        return defaultRangeSize;
    }

    public void setDefaultRangeSize(int defaultRangeSize) {
        this.defaultRangeSize = defaultRangeSize;
    }

    public long getRedisCheckIntervalMs() {
        return redisCheckIntervalMs;
    }

    public void setRedisCheckIntervalMs(long redisCheckIntervalMs) {
        this.redisCheckIntervalMs = redisCheckIntervalMs;
    }

    public long getRedisPingTimeoutMs() {
        return redisPingTimeoutMs;
    }

    public void setRedisPingTimeoutMs(long redisPingTimeoutMs) {
        this.redisPingTimeoutMs = redisPingTimeoutMs;
    }
}
