package com.cheeseocean.im.common.core.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * 基于 StringRedisTemplate 的 JSON 缓存实现。
 *
 * <p>Redis 只保存 JSON 字符串；读取时使用缓存区域声明的 {@link JavaType} 反序列化，
 * 不启用 Jackson DefaultTyping，避免把运行时类名作为跨模块存储协议。
 */
public class RedisCacheStore implements CacheStore {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisCacheStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public <T> CacheRegion<T> region(String keyPrefix, JavaType valueType, Duration ttl) {
        return new RedisCacheRegion<>(this, keyPrefix, valueType, ttl);
    }

    @Override
    public <T> CacheRegion<T> region(String keyPrefix, Class<T> valueType, Duration ttl) {
        return region(keyPrefix, objectMapper.getTypeFactory().constructType(valueType), ttl);
    }

    @Override
    public <E> CacheRegion<List<E>> listRegion(String keyPrefix, Class<E> elementType, Duration ttl) {
        JavaType valueType = objectMapper.getTypeFactory().constructCollectionType(List.class, elementType);
        return region(keyPrefix, valueType, ttl);
    }

    <T> T get(String key, JavaType valueType) {
        String json = redisTemplate.opsForValue().get(key);
        return json == null ? null : deserialize(key, json, valueType);
    }

    <T> Map<String, T> getAll(Collection<String> keys, Function<String, String> fullKey, JavaType valueType) {
        if (keys == null || keys.isEmpty()) {
            return Map.of();
        }
        List<String> logicalKeys = List.copyOf(keys);
        List<String> redisKeys = logicalKeys.stream().map(fullKey).toList();
        List<Object> values = redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            @SuppressWarnings({"rawtypes", "unchecked"})
            public Object execute(RedisOperations operations) {
                redisKeys.forEach(key -> operations.opsForValue().get(key));
                return null;
            }
        });
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, T> result = new LinkedHashMap<>();
        for (int index = 0; index < logicalKeys.size(); index++) {
            String json = stringValue(values.get(index));
            if (json != null) {
                String logicalKey = logicalKeys.get(index);
                result.put(logicalKey, deserialize(fullKey.apply(logicalKey), json, valueType));
            }
        }
        return result;
    }

    void put(String key, Object value, Duration ttl) {
        redisTemplate.opsForValue().set(key, serialize(key, value), ttl);
    }

    <T> void putAll(Map<String, T> values, Function<String, String> fullKey, Duration ttl) {
        if (values == null || values.isEmpty()) {
            return;
        }
        Map<String, String> serialized = new LinkedHashMap<>();
        for (Map.Entry<String, T> entry : values.entrySet()) {
            if (entry.getValue() != null) {
                String key = fullKey.apply(entry.getKey());
                serialized.put(key, serialize(key, entry.getValue()));
            }
        }
        if (serialized.isEmpty()) {
            return;
        }
        redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            @SuppressWarnings({"rawtypes", "unchecked"})
            public Object execute(RedisOperations operations) {
                serialized.forEach((key, json) ->
                        operations.opsForValue().set(key, json, ttl));
                return null;
            }
        });
    }

    void evict(String key) {
        redisTemplate.delete(key);
    }

    void evictAll(Collection<String> keys, Function<String, String> fullKey) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        List<String> redisKeys = keys.stream().map(fullKey).toList();
        redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            @SuppressWarnings({"rawtypes", "unchecked"})
            public Object execute(RedisOperations operations) {
                redisKeys.forEach(operations::delete);
                return null;
            }
        });
    }

    private String serialize(String key, Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("缓存序列化失败，key=" + key, exception);
        }
    }

    private <T> T deserialize(String key, String json, JavaType valueType) {
        try {
            return objectMapper.readValue(json, valueType);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("缓存反序列化失败，key=" + key, exception);
        }
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(value);
    }
}
