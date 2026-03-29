package com.cheeseocean.im.common.core.cache.redis;

import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Redis 对象缓存批量查询工具。
 *
 * 先批量读 Redis（MGET），未命中的 id 一次性批量查询数据库，再写回 Redis，
 * 最后按入参顺序返回结果，适合读多写少、一致性要求中等的场景。
 *
 * <p>依赖 {@link RedisTemplate} 配置 {@code DefaultTyping.NON_FINAL}，
 * 确保反序列化时能还原为正确的运行时类型（{@code type.isInstance(val)} 判断有效）。
 */
public final class BatchCacheHelper {

    private BatchCacheHelper() {}

    /**
     * 单条 cache-aside。
     *
     * <ol>
     *   <li>从 Redis 读取缓存，类型匹配则直接返回；</li>
     *   <li>缓存未命中时调用 {@code dbLoader} 查询数据库；</li>
     *   <li>数据库有结果则以 {@code expire} TTL 写回 Redis。</li>
     * </ol>
     *
     * @param redisTemplate Redis 操作模板
     * @param key           缓存 key
     * @param expire        缓存 TTL
     * @param dbLoader      数据库加载函数，返回 Optional
     * @param type          缓存值的运行时 Class，用于类型安全判断与强转
     * @param <V>           缓存值类型
     * @return              缓存或数据库中的对象
     */
    public static <V> Optional<V> getCache(
            RedisTemplate<String, Object> redisTemplate,
            String key,
            Duration expire,
            Supplier<Optional<V>> dbLoader,
            Class<V> type) {
        Object cached = redisTemplate.opsForValue().get(key);
        if (type.isInstance(cached)) {
            return Optional.of(type.cast(cached));
        }
        Optional<V> result = dbLoader.get();
        result.ifPresent(v -> redisTemplate.opsForValue().set(key, v, expire));
        return result;
    }

    /**
     * 批量 batchGetCache2。
     *
     * <ol>
     *   <li>将所有 {@code ids} 映射为缓存 key，批量 MGET；</li>
     *   <li>收集缓存未命中的 id 列表（{@code missIds}）；</li>
     *   <li>仅对 {@code missIds} 调用 {@code dbLoader} 批量查询数据库；</li>
     *   <li>将数据库结果以 {@code expire} TTL 逐条写回 Redis；</li>
     *   <li>按入参 {@code ids} 顺序合并结果返回（数据库和缓存中均不存在的跳过）。</li>
     * </ol>
     *
     * @param redisTemplate Redis 操作模板
     * @param expire        缓存 TTL
     * @param ids           查询的 id 列表
     * @param idToKey       id → Redis key 的映射函数
     * @param valueToId     value → id 的映射函数，用于将 DB 结果放入结果 Map
     * @param dbLoader      批量数据库查询函数，仅对未命中的 id 调用
     * @param type          缓存值的运行时 Class，用于类型安全判断与强转
     * @param <K>           id 类型
     * @param <V>           缓存值类型
     * @return              按 {@code ids} 顺序返回的结果列表
     */
    public static <K, V> List<V> batchGetCache2(
            RedisTemplate<String, Object> redisTemplate,
            Duration expire,
            List<K> ids,
            Function<K, String> idToKey,
            Function<V, K> valueToId,
            Function<List<K>, List<V>> dbLoader,
            Class<V> type) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }

        // 1. 批量读 Redis（MGET）
        List<String> cacheKeys = ids.stream().map(idToKey).collect(Collectors.toList());
        List<Object> cached = redisTemplate.opsForValue().multiGet(cacheKeys);

        // 2. 区分命中与未命中，保留 id 的原始顺序
        Map<K, V> resultMap = new LinkedHashMap<>();
        LinkedHashSet<K> missIds = new LinkedHashSet<>();
        for (int i = 0; i < ids.size(); i++) {
            K id = ids.get(i);
            Object val = (cached != null) ? cached.get(i) : null;
            if (type.isInstance(val)) {
                resultMap.put(id, type.cast(val));
            } else {
                missIds.add(id);
            }
        }

        // 3. 批量从数据库补全未命中的 id
        if (!missIds.isEmpty()) {
            dbLoader.apply(new ArrayList<>(missIds)).forEach(v -> {
                K id = valueToId.apply(v);
                resultMap.put(id, v);
                // 4. 写回 Redis
                redisTemplate.opsForValue().set(idToKey.apply(id), v, expire);
            });
        }

        // 5. 按入参顺序返回（不在数据库中的 id 直接跳过）
        return ids.stream()
                .filter(resultMap::containsKey)
                .map(resultMap::get)
                .collect(Collectors.toList());
    }
}
