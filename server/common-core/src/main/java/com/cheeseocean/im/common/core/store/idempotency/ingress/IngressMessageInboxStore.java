package com.cheeseocean.im.common.core.store.idempotency.ingress;

import java.util.List;
import java.util.Map;

/**
 * postmaster ingress 消费 inbox。
 *
 * <p>状态机以稳定 serverMsgId 对应的 key 为单位，保存处理租约与已分配 seq。
 * broker 重放可以复用原 seq；只有全部下游发布完成后才能标记 COMPLETED。</p>
 */
public interface IngressMessageInboxStore {

    /**
     * 批量申请消息处理租约，返回顺序必须与 requests 一致。
     */
    List<Claim> claimBatch(List<ClaimRequest> requests,
                           String ownerToken,
                           long nowMillis);

    /**
     * 首次分配后固定 seq；已有 seq 时返回原值，不允许覆盖。
     *
     * @return key 到稳定 seq 的映射
     */
    Map<String, Long> bindSequences(List<SequenceBinding> bindings,
                                    String ownerToken);

    /**
     * 下游 HISTORY/DELIVERY 均取得 broker ACK 后批量完成。
     */
    void completeBatch(List<String> keys, String ownerToken);

    /**
     * 明确处理失败时释放当前 owner 的租约，保留已绑定 seq。
     */
    void releaseBatch(List<String> keys, String ownerToken);

    /**
     * claim 结果是节点本地控制状态，不进入 wire 或持久化枚举编码。
     */
    enum ClaimStatus {
        ACQUIRED,
        IN_PROGRESS,
        COMPLETED,
        CONFLICT
    }

    record ClaimRequest(String key, String payloadFingerprint) {
    }

    record Claim(String key,
                 ClaimStatus status,
                 long assignedSeq,
                 long leaseUntil) {
    }

    record SequenceBinding(String key, long proposedSeq) {
    }
}
