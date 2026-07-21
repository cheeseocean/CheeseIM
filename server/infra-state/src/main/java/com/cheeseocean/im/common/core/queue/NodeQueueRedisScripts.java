package com.cheeseocean.im.common.core.queue;

import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * 节点可靠队列的 Redis 原子脚本集合。
 *
 * <p>脚本集中在基础设施层，保证 postman 普通消息生产者、postoffice 控制命令生产者
 * 与 postoffice 消费者使用相同的容量和重排语义。</p>
 */
public final class NodeQueueRedisScripts {

    /** ready 队列最大积压，超过后生产者明确返回失败，由上层执行降级。 */
    public static final long MAX_QUEUE_SIZE = 100_000L;

    /** processing 中同时在途的最大消息数，达到上限后暂停领取 ready。 */
    public static final long MAX_PROCESSING_SIZE = 10_000L;

    /** dead 队列最大保留数；溢出时淘汰最老死信，避免无限占用 Redis。 */
    public static final long MAX_DEAD_LETTER_SIZE = 10_000L;

    /** ready 队列空闲过期时间，避免已下线随机节点遗留 key 永久占用 Redis。 */
    public static final long QUEUE_TTL_SECONDS = 86_400L;

    /** 单次领取租约；实例崩溃后由同 node-id 的新实例回收。 */
    public static final long PROCESSING_LEASE_MILLIS = 60_000L;

    public static final DefaultRedisScript<Long> ENQUEUE = new DefaultRedisScript<>("""
            if redis.call('LLEN', KEYS[1]) >= tonumber(ARGV[2]) then
                return 0
            end
            redis.call('LPUSH', KEYS[1], ARGV[1])
            redis.call('EXPIRE', KEYS[1], tonumber(ARGV[3]))
            return 1
            """, Long.class);

    /** ready -> processing hash/zset，返回消息正文；processing 满时不弹出 ready。 */
    public static final DefaultRedisScript<String> CLAIM = new DefaultRedisScript<>("""
            if redis.call('HLEN', KEYS[2]) >= tonumber(ARGV[3]) then
                return nil
            end
            local payload = redis.call('RPOP', KEYS[1])
            if not payload then return nil end
            redis.call('HSET', KEYS[2], ARGV[1], payload)
            redis.call('ZADD', KEYS[3], tonumber(ARGV[2]), ARGV[1])
            redis.call('EXPIRE', KEYS[2], tonumber(ARGV[4]))
            redis.call('EXPIRE', KEYS[3], tonumber(ARGV[4]))
            return payload
            """, String.class);

    /** ACK 当前 claim。 */
    public static final DefaultRedisScript<Long> ACK = new DefaultRedisScript<>("""
            local removed = redis.call('HDEL', KEYS[1], ARGV[1])
            redis.call('ZREM', KEYS[2], ARGV[1])
            return removed
            """, Long.class);

    /**
     * claim -> ready/dead。返回 1 表示已移动，-1 表示 ready 已满并续租。
     * dead 使用 ARGV[5]=1，满载时淘汰最老元素后写入最新死信。
     */
    public static final DefaultRedisScript<Long> COMPLETE = new DefaultRedisScript<>("""
            if redis.call('HEXISTS', KEYS[1], ARGV[1]) == 0 then return 0 end
            local size = redis.call('LLEN', KEYS[3])
            if size >= tonumber(ARGV[3]) and ARGV[5] ~= '1' then
                redis.call('ZADD', KEYS[2], tonumber(ARGV[6]), ARGV[1])
                return -1
            end
            if size >= tonumber(ARGV[3]) then redis.call('RPOP', KEYS[3]) end
            redis.call('LPUSH', KEYS[3], ARGV[2])
            redis.call('EXPIRE', KEYS[3], tonumber(ARGV[4]))
            redis.call('HDEL', KEYS[1], ARGV[1])
            redis.call('ZREM', KEYS[2], ARGV[1])
            return 1
            """, Long.class);

    /** 回收一个到期 claim；ready 满时仅续租，返回 -1。 */
    public static final DefaultRedisScript<Long> RECOVER_EXPIRED = new DefaultRedisScript<>("""
            local claims = redis.call('ZRANGEBYSCORE', KEYS[2], '-inf', ARGV[1], 'LIMIT', 0, 1)
            if #claims == 0 then return 0 end
            local claim = claims[1]
            local payload = redis.call('HGET', KEYS[1], claim)
            if not payload then
                redis.call('ZREM', KEYS[2], claim)
                return 2
            end
            if redis.call('LLEN', KEYS[3]) >= tonumber(ARGV[2]) then
                redis.call('ZADD', KEYS[2], tonumber(ARGV[4]), claim)
                return -1
            end
            redis.call('LPUSH', KEYS[3], payload)
            redis.call('EXPIRE', KEYS[3], tonumber(ARGV[3]))
            redis.call('HDEL', KEYS[1], claim)
            redis.call('ZREM', KEYS[2], claim)
            return 1
            """, Long.class);

    private NodeQueueRedisScripts() {
    }
}
