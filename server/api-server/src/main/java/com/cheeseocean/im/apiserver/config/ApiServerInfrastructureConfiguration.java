package com.cheeseocean.im.apiserver.config;

import com.cheeseocean.im.common.core.store.idempotency.IdempotencyStore;
import com.cheeseocean.im.common.core.store.idempotency.redis.RedisIdempotencyStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * API 入口专属基础设施装配。
 *
 * <p>API 只需要 Redis 幂等状态，不扫描 common-core 全量实现，避免被动初始化
 * Mongo、Kafka、Chronicle、RocksDB 以及与 HTTP adapter 无关的状态机。</p>
 */
@Configuration(proxyBeanMethods = false)
public class ApiServerInfrastructureConfiguration {

    @Bean
    @ConditionalOnMissingBean(IdempotencyStore.class)
    public IdempotencyStore apiIdempotencyStore(StringRedisTemplate redisTemplate) {
        return new RedisIdempotencyStore(redisTemplate);
    }
}
