package com.cheeseocean.im.common.core.store.config;

import com.cheeseocean.im.common.core.store.conversation.ConversationStateStore;
import com.cheeseocean.im.common.core.store.conversation.redis.RedisConversationStateStore;
import com.cheeseocean.im.common.core.store.conversation.rocksdb.RocksDbConversationStateStore;
import com.cheeseocean.im.common.core.store.idempotency.IdempotencyStore;
import com.cheeseocean.im.common.core.store.idempotency.ingress.IngressMessageInboxProperties;
import com.cheeseocean.im.common.core.store.idempotency.ingress.IngressMessageInboxStore;
import com.cheeseocean.im.common.core.store.idempotency.ingress.redis.RedisIngressMessageInboxStore;
import com.cheeseocean.im.common.core.store.idempotency.ingress.rocksdb.RocksDbIngressMessageInboxStore;
import com.cheeseocean.im.common.core.store.idempotency.message.MessageSendInboxProperties;
import com.cheeseocean.im.common.core.store.idempotency.message.MessageSendInboxStore;
import com.cheeseocean.im.common.core.store.idempotency.message.redis.RedisMessageSendInboxStore;
import com.cheeseocean.im.common.core.store.idempotency.message.rocksdb.RocksDbMessageSendInboxStore;
import com.cheeseocean.im.common.core.store.idempotency.redis.RedisIdempotencyStore;
import com.cheeseocean.im.common.core.store.idempotency.rocksdb.RocksDbIdempotencyStore;
import com.cheeseocean.im.common.core.store.session.SessionStateStore;
import com.cheeseocean.im.common.core.store.session.refresh.RefreshTokenStateStore;
import com.cheeseocean.im.common.core.store.session.refresh.redis.RedisRefreshTokenStateStore;
import com.cheeseocean.im.common.core.store.session.refresh.rocksdb.RocksDbRefreshTokenStateStore;
import com.cheeseocean.im.common.core.store.session.redis.RedisSessionStateStore;
import com.cheeseocean.im.common.core.store.session.rocksdb.RocksDbSessionStateStore;
import com.cheeseocean.im.common.core.store.typing.TypingStateStore;
import com.cheeseocean.im.common.core.store.delivery.DeliveryStateStore;
import com.cheeseocean.im.common.core.store.delivery.redis.RedisDeliveryStateStore;
import com.cheeseocean.im.common.core.store.delivery.rocksdb.RocksDbDeliveryStateStore;
import com.cheeseocean.im.common.core.store.typing.redis.RedisTypingStateStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Value;

@AutoConfiguration
@ConditionalOnProperty(prefix = "cheeseim.state", name = "auto-config-enabled",
        havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({
        StateStoreProperties.class,
        MessageSendInboxProperties.class,
        IngressMessageInboxProperties.class
})
public class StateStoreAutoConfigurer {

    @Bean
    @ConditionalOnMissingBean
    @Conditional(RedisConfigurationConditions.RedisConfigured.class)
    public SessionStateStore redisSessionStateStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        return new RedisSessionStateStore(redisTemplate, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    @Conditional(RedisConfigurationConditions.RedisNotConfigured.class)
    public SessionStateStore rocksDbSessionStateStore(StateStoreProperties properties, ObjectMapper objectMapper) {
        return new RocksDbSessionStateStore(properties.resolve("session"), objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    @Conditional(RedisConfigurationConditions.RedisConfigured.class)
    public RefreshTokenStateStore redisRefreshTokenStateStore(StringRedisTemplate redisTemplate) {
        return new RedisRefreshTokenStateStore(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    @Conditional(RedisConfigurationConditions.RedisNotConfigured.class)
    public RefreshTokenStateStore rocksDbRefreshTokenStateStore(
            StateStoreProperties properties,
            ObjectMapper objectMapper) {
        return new RocksDbRefreshTokenStateStore(properties.resolve("session"), objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    @Conditional(RedisConfigurationConditions.RedisConfigured.class)
    public IdempotencyStore redisIdempotencyStore(StringRedisTemplate redisTemplate) {
        return new RedisIdempotencyStore(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    @Conditional(RedisConfigurationConditions.RedisNotConfigured.class)
    public IdempotencyStore rocksDbIdempotencyStore(StateStoreProperties properties, ObjectMapper objectMapper) {
        return new RocksDbIdempotencyStore(properties.resolve("idempotency"), objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    @Conditional(RedisConfigurationConditions.RedisConfigured.class)
    public MessageSendInboxStore redisMessageSendInboxStore(
            StringRedisTemplate redisTemplate,
            MessageSendInboxProperties properties) {
        return new RedisMessageSendInboxStore(redisTemplate, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @Conditional(RedisConfigurationConditions.RedisNotConfigured.class)
    public MessageSendInboxStore rocksDbMessageSendInboxStore(
            StateStoreProperties stateStoreProperties,
            ObjectMapper objectMapper,
            MessageSendInboxProperties properties) {
        return new RocksDbMessageSendInboxStore(
                stateStoreProperties.resolve("idempotency"),
                objectMapper,
                properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @Conditional(RedisConfigurationConditions.RedisConfigured.class)
    public IngressMessageInboxStore redisIngressMessageInboxStore(
            StringRedisTemplate redisTemplate,
            IngressMessageInboxProperties properties) {
        return new RedisIngressMessageInboxStore(redisTemplate, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @Conditional(RedisConfigurationConditions.RedisNotConfigured.class)
    public IngressMessageInboxStore rocksDbIngressMessageInboxStore(
            StateStoreProperties stateStoreProperties,
            ObjectMapper objectMapper,
            IngressMessageInboxProperties properties) {
        return new RocksDbIngressMessageInboxStore(
                stateStoreProperties.resolve("idempotency"),
                objectMapper,
                properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @Conditional(RedisConfigurationConditions.RedisConfigured.class)
    public ConversationStateStore redisConversationStateStore(StringRedisTemplate redisTemplate) {
        return new RedisConversationStateStore(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    @Conditional(RedisConfigurationConditions.RedisConfigured.class)
    public DeliveryStateStore redisDeliveryStateStore(
            StringRedisTemplate redisTemplate,
            @Value("${cheeseim.delivery-state.redis-ttl-seconds:2592000}") long ttlSeconds) {
        return new RedisDeliveryStateStore(redisTemplate, ttlSeconds);
    }

    @Bean
    @ConditionalOnMissingBean
    @Conditional(RedisConfigurationConditions.RedisNotConfigured.class)
    public DeliveryStateStore rocksDbDeliveryStateStore(StateStoreProperties properties, ObjectMapper objectMapper) {
        return new RocksDbDeliveryStateStore(properties.resolve("delivery"), objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    @Conditional(RedisConfigurationConditions.RedisConfigured.class)
    public TypingStateStore redisTypingStateStore(StringRedisTemplate redisTemplate) {
        return new RedisTypingStateStore(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    @Conditional(RedisConfigurationConditions.RedisNotConfigured.class)
    public ConversationStateStore rocksDbConversationStateStore(StateStoreProperties properties, ObjectMapper objectMapper) {
        return new RocksDbConversationStateStore(properties.resolve("conversation"), objectMapper);
    }

    @Bean
    @Conditional(RedisConfigurationConditions.ClusterModeWithoutRedis.class)
    public Object clusterStateStoreRequiresRedis() {
        throw new IllegalStateException("cluster mode requires spring.data.redis host/url/sentinel/cluster configuration");
    }
}
