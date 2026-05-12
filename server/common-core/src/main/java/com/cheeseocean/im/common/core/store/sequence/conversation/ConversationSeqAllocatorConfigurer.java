package com.cheeseocean.im.common.core.store.sequence.conversation;

import com.cheeseocean.im.common.core.business.repository.ConversationSequenceRepository;
import com.cheeseocean.im.common.core.store.config.StateStoreProperties;
import com.cheeseocean.im.common.core.store.sequence.conversation.redis.RedisConversationSeqCacheStore;
import com.cheeseocean.im.common.core.store.sequence.conversation.rocksdb.RocksDbConversationSeqCacheStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;

/**
 * 会话 seq 分配器自动配置。
 *
 * <p>部署约束：
 * <ul>
 *   <li>单机模式：允许 Redis 或 RocksDB 作为缓存段</li>
 *   <li>集群模式：必须使用 Redis 作为共享缓存段</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(ConversationSeqAllocatorProperties.class)
public class ConversationSeqAllocatorConfigurer {

    @Bean
    @ConditionalOnMissingBean
    public ConversationSeqCacheStore conversationSeqCacheStore(@Nullable StringRedisTemplate redisTemplate,
                                                               StateStoreProperties stateStoreProperties,
                                                               ConversationSeqAllocatorProperties properties) {
        if (redisTemplate != null) {
            // Redis 是首选实现，单机和集群都可以使用。
            return new RedisConversationSeqCacheStore(
                    redisTemplate,
                    properties.getLockTtlSeconds(),
                    properties.getDataTtlSeconds()
            );
        }
        if (properties.getDeploymentMode() == ConversationSeqDeploymentMode.CLUSTER) {
            throw new IllegalStateException("Cluster mode requires Redis-backed conversation seq cache");
        }
        // 只有明确单机部署时才允许 RocksDB 顶替缓存层。
        return new RocksDbConversationSeqCacheStore(stateStoreProperties.resolve("conversation-seq"));
    }

    @Bean
    @ConditionalOnMissingBean
    public ConversationSeqAllocator conversationSeqAllocator(ConversationSeqCacheStore cacheStore,
                                                             ConversationSequenceRepository conversationSequenceRepository,
                                                             ConversationSeqAllocatorProperties properties) {
        return new ConversationSeqAllocator(
                cacheStore,
                conversationSequenceRepository,
                properties.getSingleReserveSize(),
                properties.getGroupReserveSize(),
                properties.getMaxRetries()
        );
    }
}
