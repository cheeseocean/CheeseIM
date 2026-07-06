package com.cheeseocean.im.postoffice.dedup;

import com.cheeseocean.im.common.core.constants.RedisKeys;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 单元测试覆盖 {@link RedisDeliveryDedupStore#markIfAbsent} 的关键行为：
 * <ul>
 *   <li>调用 {@code StringRedisTemplate.opsForValue().setIfAbsent(key, "1", ttl)}
 *       这一单原子命令，避免旧 {@code EXISTS + SET} 之间的 TOCTOU 竞态</li>
 *   <li>argv 顺序与 TTL 正确（TTL 来自 property，默认 600 秒）</li>
 *   <li>deviceId 为 null 时回退为通配符 *，与旧本地 Set 的 key 拼字符串规则一致</li>
 *   <li>null Redis 返回（异常/网络抖动）按 false 处理，让上游走重复分支，避免重复推送</li>
 * </ul>
 */
class RedisDeliveryDedupStoreTest {

    @Test
    @SuppressWarnings("unchecked")
    void markIfAbsentShouldIssueSetNxExWithBuiltKeyAndTtl() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(
                eq(RedisKeys.deliveryIdem("srv-1", "userB", "ios-1")),
                eq("1"),
                eq(Duration.ofSeconds(600))))
                .thenReturn(true);

        RedisDeliveryDedupStore store = new RedisDeliveryDedupStore(redisTemplate, 600L);

        assertTrue(store.markIfAbsent("srv-1", "userB", "ios-1"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void markIfAbsentShouldReplaceNullDeviceIdWithWildcard() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        // 未指定 deviceId，key 应使用通配符 "*"，保持与旧本地 Set 拼接语义一致
        when(valueOps.setIfAbsent(
                eq(RedisKeys.deliveryIdem("srv-1", "userB", "*")),
                eq("1"),
                eq(Duration.ofSeconds(600))))
                .thenReturn(true);

        RedisDeliveryDedupStore store = new RedisDeliveryDedupStore(redisTemplate, 600L);

        assertTrue(store.markIfAbsent("srv-1", "userB", null));
    }

    @Test
    @SuppressWarnings("unchecked")
    void markIfAbsentShouldReturnFalseWhenKeyAlreadyExists() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        // 已存在：SET NX 返回 false / null。这里直接返回 false 表示重复投递。
        when(valueOps.setIfAbsent(
                eq(RedisKeys.deliveryIdem("srv-1", "userB", "ios-1")),
                eq("1"),
                eq(Duration.ofSeconds(600))))
                .thenReturn(false);

        RedisDeliveryDedupStore store = new RedisDeliveryDedupStore(redisTemplate, 600L);

        assertFalse(store.markIfAbsent("srv-1", "userB", "ios-1"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void markIfAbsentShouldReturnFalseWhenRedisReturnsNull() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        // Redis 在异常/网络抖动场景返回 null：按"已记录过"语义处理，避免重复推送
        when(valueOps.setIfAbsent(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Duration.class)))
                .thenReturn(null);

        RedisDeliveryDedupStore store = new RedisDeliveryDedupStore(redisTemplate, 600L);

        assertFalse(store.markIfAbsent("srv-1", "userB", "ios-1"));
    }

    @Test
    void markIfAbsentShouldShortCircuitOnNullServerMsgIdOrUserId() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

        RedisDeliveryDedupStore store = new RedisDeliveryDedupStore(redisTemplate, 600L);

        // 与旧本地 Set 行为一致：入参缺失直接返回 false，不调用 Redis
        assertFalse(store.markIfAbsent(null, "userB", "ios-1"));
        assertFalse(store.markIfAbsent("srv-1", null, "ios-1"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void markIfAbsentShouldClampTtlToAtLeastOneSecond() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        // 0 或负数 TTL 应被钳制为 1 秒，避免 SET EX 0 在 Redis 中变成无 TTL 的永久 key
        when(valueOps.setIfAbsent(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                eq(Duration.ofSeconds(1))))
                .thenReturn(true);

        RedisDeliveryDedupStore store = new RedisDeliveryDedupStore(redisTemplate, 0L);

        // 调用 setIfAbsent 时 TTL 应已被钳为 1 秒（mock 校验传入参数）
        assertTrue(store.markIfAbsent("srv-1", "userB", "ios-1"));
    }
}