package com.cheeseocean.im.common.core.cache;

import com.fasterxml.jackson.databind.JavaType;

import java.time.Duration;
import java.util.List;

/**
 * 远端缓存访问边界。
 *
 * <p>缓存值的反序列化类型由调用方显式声明，禁止依赖 Redis 全局多态类型信息。
 */
public interface CacheStore {

    /**
     * 创建一个具有固定 key 前缀、值类型与默认过期时间的缓存区域。
     */
    <T> CacheRegion<T> region(String keyPrefix, JavaType valueType, Duration ttl);

    /** 创建简单对象区域。 */
    <T> CacheRegion<T> region(String keyPrefix, Class<T> valueType, Duration ttl);

    /** 创建列表区域，元素类型同样显式固定。 */
    <E> CacheRegion<List<E>> listRegion(String keyPrefix, Class<E> elementType, Duration ttl);
}
