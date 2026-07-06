package com.cheeseocean.im.postoffice.dedup;

/**
 * 投递去重存储抽象。
 *
 * <p>对每条 (serverMsgId, userId, deviceId) 元组记录"已投递过"一次，幂等保护下游
 * 重发导致同一连接同一消息被推送多次。底层实现可以是 Redis（生产，跨节点共享，
 * 自动过期）或单元测试中注入的内存伪造。
 *
 * <p>该接口不强制要求读取，<b>只在 mark-if-absent 语义下使用</b>，以保证原子性。
 */
public interface DeliveryDedupStore {

    /**
     * 若 (serverMsgId, userId, deviceId) 元组此前未记录则记录并返回 {@code true}；
     * 若已存在则返回 {@code false} 表示重复投递，需要调用方跳过本次推送。
     *
     * <p>实现需保证原子性（见 {@link RedisDeliveryDedupStore} 用 Redis {@code SET NX EX}
     * 单命令），并按 TTL 自动过期，避免内存无限增长。
     *
     * @param serverMsgId 服务端消息 ID，非空
     * @param userId      接收方用户 ID，非空
     * @param deviceId    设备 ID，可为 null（null 时按通配符 "*" 记录，等价于"任何设备均标记为已投递"）
     * @return {@code true} 表示本次是首次记录，可执行真正的投递；{@code false} 表示已记录过，调用方应跳过
     */
    boolean markIfAbsent(String serverMsgId, String userId, String deviceId);
}