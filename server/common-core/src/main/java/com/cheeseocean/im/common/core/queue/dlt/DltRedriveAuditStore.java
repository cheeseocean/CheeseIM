package com.cheeseocean.im.common.core.queue.dlt;

/**
 * DLT 重放操作的租约与审计存储。
 *
 * <p>claim 状态仅用于节点本地流程，不持久化为 ordinal。</p>
 */
public interface DltRedriveAuditStore {

    Claim claim(DltRedriveCommand command,
                String checksum,
                String ownerToken,
                long nowMillis,
                long leaseMillis);

    boolean complete(String operationId,
                     String ownerToken,
                     long generation,
                     long completedAt);

    void fail(String operationId,
              String ownerToken,
              long generation,
              long failedAt,
              String error);

    enum ClaimStatus {
        ACQUIRED,
        BUSY,
        COMPLETED
    }

    record Claim(ClaimStatus status, long generation, long leaseUntil) {
    }
}
