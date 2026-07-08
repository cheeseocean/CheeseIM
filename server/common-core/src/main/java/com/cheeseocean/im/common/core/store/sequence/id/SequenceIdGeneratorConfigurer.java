package com.cheeseocean.im.common.core.store.sequence.id;

import com.cheeseocean.im.common.core.store.config.StateStoreProperties;
import com.cheeseocean.im.common.core.store.config.RedisConfigurationConditions;
import com.cheeseocean.im.common.core.store.sequence.SequenceStore;
import com.cheeseocean.im.common.core.store.sequence.redis.RedisSequenceStore;
import com.cheeseocean.im.common.core.store.sequence.rocksdb.RocksDbSequenceStore;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * SequenceIdGenerator 自动配置
 * <p>
 * 根据是否配置 Redis 选择不同的分配策略：
 * <ul>
 *   <li>配置了 Redis：Redis 主路径 + RocksDB 降级，后台健康探活</li>
 *   <li>未配置 Redis：仅使用 RocksDB，无探活开销</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(SequenceIdGeneratorProperties.class)
public class SequenceIdGeneratorConfigurer {

    /**
     * Redis 可用时：主路径 Redis + 降级路径 RocksDB + 后台健康探活
     */
    @Bean
    @ConditionalOnMissingBean
    @Conditional(RedisConfigurationConditions.RedisConfigured.class)
    public SequenceIdGenerator sequenceIdGenerator(
            StringRedisTemplate stringRedisTemplate,
            StateStoreProperties storeProperties,
            SequenceIdGeneratorProperties properties,
            MeterRegistry meterRegistry) {

        SequenceStore primaryStore = new RedisSequenceStore(stringRedisTemplate);
        SequenceStore fallbackStore = new RocksDbSequenceStore(storeProperties.resolve("seqid"));
        HealthMonitor healthMonitor = new RedisHealthMonitor(
                stringRedisTemplate,
                Duration.ofMillis(properties.getRedisCheckIntervalMs()),
                Duration.ofMillis(properties.getRedisPingTimeoutMs()));

        return new SequenceIdGenerator(
                primaryStore, fallbackStore, healthMonitor,
                properties.getDefaultRangeSize(), meterRegistry);
    }

    /**
     * Redis 未配置时：仅使用 RocksDB，始终走本地持久化路径
     */
    @Bean
    @ConditionalOnMissingBean
    @Conditional(RedisConfigurationConditions.RedisNotConfigured.class)
    public SequenceIdGenerator rocksDbOnlySequenceIdGenerator(
            StateStoreProperties storeProperties,
            SequenceIdGeneratorProperties properties,
            MeterRegistry meterRegistry) {

        SequenceStore store = new RocksDbSequenceStore(storeProperties.resolve("seqid"));
        // 无 Redis 时，HealthMonitor 始终返回不可用，所有申请走 RocksDB
        return new SequenceIdGenerator(
                store, store, HealthMonitor.alwaysDown(),
                properties.getDefaultRangeSize(), meterRegistry);
    }
}
