package com.cheeseocean.im.postoffice.dedup;

import com.cheeseocean.im.common.core.constants.RedisKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;

/**
 * 投递去重的 Redis 实现。
 *
 * <p>使用 Redis 单命令 {@code SET <key> 1 NX EX <ttl>} 做原子 mark-if-absent：
 * <ul>
 *   <li>原子性：Redis 单线程执行 {@code SET NX EX}，避免 {@code EXISTS + SET} 的 TOCTOU 竞态</li>
 *   <li>跨节点共享：多 postoffice 节点共用同一 Redis，跨节点的重复推送也会被去重</li>
 *   <li>无界增长问题修复：旧实现是本地 {@code ConcurrentHashMap.newKeySet()}，长跑 OOM；
 *       Redis 实现每个去重记录一个独立 key 并由 TTL 自动回收，key 数与"近 TTL 窗口内的投递数"
 *       成正比，与进程生命期无关（ASSESSMENT P0-5 修复点）</li>
 * </ul>
 *
 * <p>Key 形式：{@code idem:delivery:{serverMsgId}:{userId}:{deviceId|*}}，复用
 * {@link RedisKeys#deliveryIdem}（与既有命名约定一致）。deviceId 为 null 时替换为通配符 *，
 * 与旧本地 Set 的拼字符串行为保持等价。
 */
@Service
public class RedisDeliveryDedupStore implements DeliveryDedupStore {

    private static final Logger logger = LoggerFactory.getLogger(RedisDeliveryDedupStore.class);

    /** TTL。默认 10 分钟：覆盖典型的客户端重试窗口，过期后允许再次"投递记录"（此时上游也不会再发同一 serverMsgId）。 */
    public static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    private static final String FLAG = "1";

    private final StringRedisTemplate redisTemplate;
    private final Duration dedupTtl;

    public RedisDeliveryDedupStore(StringRedisTemplate redisTemplate,
                                   @Value("${cheeseim.delivery.dedup.ttl-seconds:600}") long dedupTtlSeconds) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
        this.dedupTtl = Duration.ofSeconds(Math.max(1L, dedupTtlSeconds));
    }

    @Override
    public boolean markIfAbsent(String serverMsgId, String userId, String deviceId) {
        if (serverMsgId == null || userId == null) {
            // 入参缺失时不写入 Redis，并发不会因为 serverMsgId==null 而误判首投。
            // 返回 false 等同于"已记录过"，让上层 ConnectionManager 走 DUPLICATE 分支，
            // 与旧本地 Set 的 null 短路行为保持一致（见 ConnectionManager.markDeliveryIfAbsent 改造）。
            return false;
        }
        String deviceKey = deviceId == null ? "*" : deviceId;
        String key = RedisKeys.deliveryIdem(serverMsgId, userId, deviceKey);
        Boolean result = redisTemplate.opsForValue().setIfAbsent(key, FLAG, dedupTtl);
        if (result == null) {
            // 极少数 Redis 异常时返回 null；返回 false 让上游跳过本次，避免重复推送。
            logger.warn("Redis delivery dedup returned null for key={}", key);
            return false;
        }
        return result;
    }
}