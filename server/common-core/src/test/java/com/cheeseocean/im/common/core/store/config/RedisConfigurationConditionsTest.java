package com.cheeseocean.im.common.core.store.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class RedisConfigurationConditionsTest {

    @Test
    void redisConfiguredShouldMatchBoot3SentinelAndClusterProperties() {
        MockEnvironment sentinel = new MockEnvironment()
                .withProperty("spring.data.redis.sentinel.nodes", "redis-a:26379,redis-b:26379");
        MockEnvironment cluster = new MockEnvironment()
                .withProperty("spring.data.redis.cluster.nodes", "redis-a:6379,redis-b:6379");

        assertThat(RedisConfigurationConditions.isRedisConfigured(sentinel)).isTrue();
        assertThat(RedisConfigurationConditions.isRedisConfigured(cluster)).isTrue();
    }

    @Test
    void redisConfiguredShouldMatchModuleDatabaseOnlyProperty() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.data.redis.database", "1");

        assertThat(RedisConfigurationConditions.isRedisConfigured(environment)).isTrue();
    }

    @Test
    void redisConfiguredShouldIgnoreMissingRedisProperties() {
        assertThat(RedisConfigurationConditions.isRedisConfigured(new MockEnvironment())).isFalse();
    }
}
