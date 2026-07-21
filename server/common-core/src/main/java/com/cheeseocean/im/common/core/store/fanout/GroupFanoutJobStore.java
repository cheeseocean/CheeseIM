package com.cheeseocean.im.common.core.store.fanout;

/**
 * 群扩散任务租约与分页进度存储。
 *
 * <p>claim 状态仅用于 worker 本地流程，不进入 wire 或持久化，因此不需要稳定 code。</p>
 */
public interface GroupFanoutJobStore {

    Claim claim(String jobId,
                long membershipVersion,
                String ownerToken,
                long nowMillis,
                long leaseMillis);

    boolean checkpoint(String jobId,
                       String ownerToken,
                       long generation,
                       long joinedVersion,
                       String userId,
                       String epochId,
                       long leaseUntil);

    boolean complete(String jobId, String ownerToken, long generation, long completedAt);

    void release(String jobId, String ownerToken, long generation);

    enum ClaimStatus {
        ACQUIRED,
        BUSY,
        COMPLETED
    }

    record Claim(ClaimStatus status,
                 long generation,
                 long leaseUntil,
                 long membershipVersion,
                 long joinedVersion,
                 String userId,
                 String epochId) {
    }
}
