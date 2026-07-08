package com.cheeseocean.im.common.core.store.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * Redis 运行条件。
 *
 * <p>Spring Boot 3 的 Redis 配置主路径是 {@code spring.data.redis.*}，
 * 老代码只看 {@code spring.redis.host} 会在 sentinel/cluster profile 下误选本地 RocksDB。
 */
public final class RedisConfigurationConditions {

    private RedisConfigurationConditions() {
    }

    public static boolean isRedisConfigured(Environment environment) {
        return hasText(environment, "spring.redis.host")
                || hasText(environment, "spring.data.redis.host")
                || hasText(environment, "spring.data.redis.port")
                || hasText(environment, "spring.data.redis.url")
                || hasText(environment, "spring.data.redis.database")
                || hasText(environment, "spring.data.redis.sentinel.nodes")
                || hasText(environment, "spring.data.redis.cluster.nodes");
    }

    public static final class RedisConfigured implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return isRedisConfigured(context.getEnvironment());
        }
    }

    public static final class RedisNotConfigured implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return !isRedisConfigured(context.getEnvironment());
        }
    }

    public static final class ClusterModeWithoutRedis implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            Environment environment = context.getEnvironment();
            return "cluster".equalsIgnoreCase(environment.getProperty("cheeseim.runtime.mode"))
                    && !isRedisConfigured(environment);
        }
    }

    private static boolean hasText(Environment environment, String key) {
        return StringUtils.hasText(environment.getProperty(key));
    }
}
