package com.cheeseocean.im.common.core.cache.config;

import com.cheeseocean.im.common.core.cache.CacheStore;
import com.cheeseocean.im.common.core.cache.RedisCacheStore;
import com.cheeseocean.im.common.core.store.config.RedisConfigurationConditions;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration
@ConditionalOnProperty(prefix = "cheeseim.state", name = "auto-config-enabled",
        havingValue = "true", matchIfMissing = true)
public class CacheAutoConfigurer {

    @Bean
    @ConditionalOnMissingBean
    @Conditional(RedisConfigurationConditions.RedisConfigured.class)
    public CacheStore cacheStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        return new RedisCacheStore(redisTemplate, objectMapper);
    }
}
