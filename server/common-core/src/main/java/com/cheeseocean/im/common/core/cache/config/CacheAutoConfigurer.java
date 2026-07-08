package com.cheeseocean.im.common.core.cache.config;

import com.cheeseocean.im.common.core.cache.L2CacheAdapter;
import com.cheeseocean.im.common.core.cache.MultiLevelCacheService;
import com.cheeseocean.im.common.core.cache.redis.RedisL2CacheAdapter;
import com.cheeseocean.im.common.core.cache.rocksdb.RocksDbL2CacheAdapter;
import com.cheeseocean.im.common.core.store.config.RedisConfigurationConditions;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

@Configuration
@EnableConfigurationProperties(CacheProperties.class)
public class CacheAutoConfigurer {

    @Bean
    @ConditionalOnMissingBean(name = "l1Cache")
    public Cache<String, Object> l1Cache(CacheProperties properties) {
        return Caffeine.newBuilder().maximumSize(properties.getL1MaximumSize()).build();
    }

    @Bean
    @ConditionalOnMissingBean
    @Conditional(RedisConfigurationConditions.RedisConfigured.class)
    public L2CacheAdapter redisL2CacheAdapter(RedisTemplate<String, Object> redisTemplate) {
        return new RedisL2CacheAdapter(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    @Conditional(RedisConfigurationConditions.RedisNotConfigured.class)
    public L2CacheAdapter rocksDbL2CacheAdapter(CacheProperties properties, ObjectMapper objectMapper) {
        return new RocksDbL2CacheAdapter(properties.resolve("l2"), objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public MultiLevelCacheService multiLevelCacheService(Cache<String, Object> l1Cache, L2CacheAdapter l2CacheAdapter) {
        return new MultiLevelCacheService(l1Cache, l2CacheAdapter);
    }
}
