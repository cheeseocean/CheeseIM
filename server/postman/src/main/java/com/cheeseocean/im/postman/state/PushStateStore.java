package com.cheeseocean.im.postman.state;

import com.cheeseocean.im.common.api.enums.DeliveryState;
import com.cheeseocean.im.postman.entity.PushAttempt;

import java.util.Optional;

/**
 * 离线推送的跨副本状态存储。
 *
 * <p>同一服务端消息的推送尝试和投递状态必须在同一个共享存储中原子判断，
 * 避免不同 postman 副本对同一用户重复调用厂商推送。
 */
public interface PushStateStore {

    /**
     * 原子申请一次离线推送尝试。
     */
    PushClaim claimPush(String serverMsgId, String userId);

    /**
     * 取消尚未完成的推送尝试。
     */
    void cancelAttempt(String serverMsgId, String userId);

    /**
     * 记录用户维度的消息投递状态。
     */
    void recordDeliveryState(String serverMsgId, String userId, DeliveryState state);

    /**
     * 查询指定用户的推送尝试。
     */
    Optional<PushAttempt> findAttempt(String serverMsgId, String userId);

    /**
     * 查询同一消息的任一推送尝试，用于兼容未携带 userId 的旧调用。
     */
    Optional<PushAttempt> findAnyAttempt(String serverMsgId);

    /**
     * 原子预占用户当日的一次推送配额。
     */
    boolean claimDailyQuota(String userId, int maxDailyCount);

    /**
     * 归还本次未成功发送的当日推送配额。
     */
    void releaseDailyQuota(String userId);

    /**
     * 返回用户当日已预占的推送次数。
     */
    int getDailyPushCount(String userId);

    /**
     * 一次 claim 的原子判定结果。
     */
    record PushClaim(PushAttempt claimedAttempt, DeliveryState deliveryState, boolean duplicateAttempt) {
        public boolean claimed() {
            return claimedAttempt != null;
        }
    }
}
