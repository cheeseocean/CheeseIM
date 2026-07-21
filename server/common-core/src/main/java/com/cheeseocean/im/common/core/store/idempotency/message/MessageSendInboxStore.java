package com.cheeseocean.im.common.core.store.idempotency.message;

/**
 * 消息发送入口 inbox。
 *
 * <p>同一 {@code senderId + conversationId + clientMsgId} 只生成一个 serverMsgId。
 * claim 使用短租约避免并发重复发布；进程在 broker ACK 前后崩溃时，后续请求可以在租约过期后
 * 复用原 serverMsgId 继续发布，为下游按稳定消息 ID 去重提供前提。</p>
 */
public interface MessageSendInboxStore {

    Claim claim(String key,
                String payloadFingerprint,
                String proposedServerMsgId,
                String ownerToken,
                long nowMillis);

    /**
     * 固定首次权限检查得到的离线推送决策。
     *
     * <p>broker ACK 不明确时，后续租约恢复必须复用同一决策，避免同一个 serverMsgId
     * 因用户免打扰配置变化而产生不同 ingress 载荷。</p>
     *
     * @return 首次已固定的值；并发重复调用不会覆盖
     */
    boolean bindEffectiveOfflinePush(String key,
                                     String ownerToken,
                                     boolean needOfflinePush);

    /**
     * 将已获得 broker ACK 的消息标记为 ACCEPTED。
     *
     * @return inbox 仍存在且消息身份匹配时返回稳定的 acceptedAt，否则抛出异常
     */
    long markAccepted(String key,
                      String payloadFingerprint,
                      String serverMsgId,
                      long acceptedAt);

    /**
     * 发布明确失败时释放当前 owner 的租约，但保留 serverMsgId，供客户端重试复用。
     */
    void release(String key, String ownerToken);

    enum ClaimStatus {
        ACQUIRED,
        IN_PROGRESS,
        ACCEPTED,
        CONFLICT
    }

    record Claim(ClaimStatus status,
                 String serverMsgId,
                 long createdAt,
                 long acceptedAt,
                 Boolean effectiveOfflinePush) {
    }
}
