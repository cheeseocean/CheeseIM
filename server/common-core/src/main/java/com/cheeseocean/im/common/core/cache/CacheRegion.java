package com.cheeseocean.im.common.core.cache;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 单一数据形态的缓存区域。
 *
 * <p>区域将 key 前缀、值类型和 TTL 固定在创建处，使业务代码无需选择 Redis 序列化方式，
 * 也避免不同调用方对同一 key 使用不一致的反序列化类型。
 */
public interface CacheRegion<T> {

    T get(String key);

    Map<String, T> getAll(Collection<String> keys);

    T getOrLoad(String key, Supplier<T> loader);

    void put(String key, T value);

    /** 使用指定 TTL 写入，适用于 token 等由业务动态决定过期时间的值。 */
    void put(String key, T value, Duration ttl);

    void putAll(Map<String, T> values);

    void evict(String key);

    void evictAll(Collection<String> keys);
}
