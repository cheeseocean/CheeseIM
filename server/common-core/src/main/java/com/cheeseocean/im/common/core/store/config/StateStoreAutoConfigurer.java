package com.cheeseocean.im.common.core.store.config;

import com.cheeseocean.im.common.core.store.conversation.ConversationStateStore;
import com.cheeseocean.im.common.core.store.conversation.redis.RedisConversationStateStore;
import com.cheeseocean.im.common.core.store.conversation.rocksdb.RocksDbConversationStateStore;
import com.cheeseocean.im.common.core.store.idempotency.IdempotencyStore;
import com.cheeseocean.im.common.core.store.idempotency.redis.RedisIdempotencyStore;
import com.cheeseocean.im.common.core.store.idempotency.rocksdb.RocksDbIdempotencyStore;
import com.cheeseocean.im.common.core.store.sequence.ConversationSequenceAllocator;
import com.cheeseocean.im.common.core.store.sequence.SequenceStore;
import com.cheeseocean.im.common.core.store.sequence.redis.RedisSequenceStore;
import com.cheeseocean.im.common.core.store.sequence.rocksdb.RocksDbSequenceStore;
import com.cheeseocean.im.common.core.store.session.SessionStateStore;
import com.cheeseocean.im.common.core.store.session.redis.RedisSessionStateStore;
import com.cheeseocean.im.common.core.store.session.rocksdb.RocksDbSessionStateStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@EnableConfigurationProperties(StateStoreProperties.class)
public class StateStoreAutoConfigurer {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spring.redis", name = "host")
    public SessionStateStore redisSessionStateStore(RedisTemplate<String, Object> redisTemplate) {
        return new RedisSessionStateStore(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spring.redis", name = "host", matchIfMissing = true, havingValue = "")
    public SessionStateStore rocksDbSessionStateStore(StateStoreProperties properties, ObjectMapper objectMapper) {
        return new RocksDbSessionStateStore(properties.resolve("session"), objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spring.redis", name = "host")
    public IdempotencyStore redisIdempotencyStore(StringRedisTemplate redisTemplate) {
        return new RedisIdempotencyStore(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spring.redis", name = "host", matchIfMissing = true, havingValue = "")
    public IdempotencyStore rocksDbIdempotencyStore(StateStoreProperties properties, ObjectMapper objectMapper) {
        return new RocksDbIdempotencyStore(properties.resolve("idempotency"), objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spring.redis", name = "host")
    public SequenceStore redisSequenceStore(StringRedisTemplate redisTemplate) {
        return new RedisSequenceStore(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spring.redis", name = "host", matchIfMissing = true, havingValue = "")
    public SequenceStore rocksDbSequenceStore(StateStoreProperties properties, ObjectMapper objectMapper) {
        return new RocksDbSequenceStore(properties.resolve("sequence"), objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public ConversationSequenceAllocator conversationSequenceAllocator(SequenceStore sequenceStore,
                                                                       StateStoreProperties properties) {
        return new ConversationSequenceAllocator(sequenceStore, properties.getSequenceRangeSize());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spring.redis", name = "host")
    public ConversationStateStore redisConversationStateStore(StringRedisTemplate redisTemplate) {
        return new RedisConversationStateStore(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spring.redis", name = "host", matchIfMissing = true, havingValue = "")
    public ConversationStateStore rocksDbConversationStateStore(StateStoreProperties properties, ObjectMapper objectMapper) {
        return new RocksDbConversationStateStore(properties.resolve("conversation"), objectMapper);
    }
}
