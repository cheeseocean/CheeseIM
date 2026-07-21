package com.cheeseocean.im.common.core.store.idempotency.message;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 消息发送 inbox 的保留期与发布租约配置。
 */
@ConfigurationProperties(prefix = "cheeseim.message-send-inbox")
public class MessageSendInboxProperties {

    private long ttlSeconds = 604_800L;
    private long leaseSeconds = 30L;

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public long getLeaseSeconds() {
        return leaseSeconds;
    }

    public void setLeaseSeconds(long leaseSeconds) {
        this.leaseSeconds = leaseSeconds;
    }

    public long normalizedTtlSeconds() {
        return Math.max(60L, ttlSeconds);
    }

    public long normalizedLeaseMillis() {
        return Math.max(1L, leaseSeconds) * 1_000L;
    }
}
