package com.cheeseocean.im.common.core.store.idempotency.ingress;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ingress inbox 保留期与处理租约配置。
 */
@ConfigurationProperties(prefix = "cheeseim.ingress-inbox")
public class IngressMessageInboxProperties {

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
