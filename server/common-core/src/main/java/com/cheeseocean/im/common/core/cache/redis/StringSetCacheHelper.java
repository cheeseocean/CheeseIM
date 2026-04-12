package com.cheeseocean.im.common.core.cache.redis;

import org.springframework.data.redis.core.RedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Redis 字符串集合 cache-aside 工具。
 *
 * <p>使用一个 Redis SET 存储真实数据，再配套一个 loaded 标记 key 表达
 * “空集合已加载过”，避免空集合场景持续回源数据库。
 */
public final class StringSetCacheHelper {

    private StringSetCacheHelper() {
    }

    public static List<String> getOrLoad(
            RedisTemplate<String, Object> redisTemplate,
            String dataKey,
            String loadedKey,
            Supplier<List<String>> dbLoader) {
        Set<Object> cached = redisTemplate.opsForSet().members(dataKey);
        if (cached != null && !cached.isEmpty()) {
            return cached.stream().map(String::valueOf).toList();
        }
        if (Boolean.TRUE.equals(redisTemplate.hasKey(loadedKey))) {
            return new ArrayList<>();
        }

        List<String> loaded = new ArrayList<>(dbLoader.get());
        redisTemplate.delete(dataKey);
        if (!loaded.isEmpty()) {
            redisTemplate.opsForSet().add(dataKey, loaded.toArray());
        }
        redisTemplate.opsForValue().set(loadedKey, true);
        return loaded;
    }

    public static void markLoaded(RedisTemplate<String, Object> redisTemplate, String loadedKey) {
        redisTemplate.opsForValue().set(loadedKey, true);
    }

    public static boolean containsOrLoad(
            RedisTemplate<String, Object> redisTemplate,
            String dataKey,
            String loadedKey,
            String member,
            BooleanSupplier dbLoader) {
        if (Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(dataKey, member))) {
            return true;
        }
        if (Boolean.TRUE.equals(redisTemplate.hasKey(loadedKey))) {
            return false;
        }

        boolean loaded = dbLoader.getAsBoolean();
        if (loaded) {
            redisTemplate.opsForSet().add(dataKey, member);
        }
        return loaded;
    }
}
