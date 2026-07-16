package com.cheeseocean.im.postoffice.dedup;

/**
 * 在线投递去重状态机。
 *
 * <p>调用方必须先 claim，传输成功后 commit，失败时 abort。这样发送失败不会留下永久的
 * delivered 标记；同一时刻只有一个调用方能持有 claim。</p>
 */
public interface DeliveryDedupStore {

    Claim claim(String deliveryId, String userId, String deviceId);

    boolean commit(Claim claim);

    boolean abort(Claim claim);

    enum ClaimStatus {
        ACQUIRED,
        DELIVERED,
        IN_PROGRESS,
        UNAVAILABLE
    }

    record Claim(ClaimStatus status, String key, String token) {
        public static Claim acquired(String key, String token) {
            return new Claim(ClaimStatus.ACQUIRED, key, token);
        }

        public static Claim status(ClaimStatus status) {
            return new Claim(status, null, null);
        }
    }
}
