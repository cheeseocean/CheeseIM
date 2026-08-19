package com.cheeseocean.im.common.core.business.mongo.config;

import com.cheeseocean.im.common.core.business.transaction.MongoPersistenceTransactionExecutor;
import com.cheeseocean.im.common.core.business.transaction.PersistenceTransactionExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Shared Mongo persistence registration for common-core owned business data.
 */
@AutoConfiguration(after = {MongoAutoConfiguration.class, MongoDataAutoConfiguration.class})
// storage-business 本身是 Mongo adapter，依赖它的运行时必然携带 MongoTemplate 类。
// 不用 ConditionalOnBean：all-in-one 的跨模块扫描可能早于 MongoAutoConfiguration 解析本类，
// 会把永久存在的 Mongo bean 误判为缺失，导致整个持久层未注册。
@ConditionalOnClass(MongoTemplate.class)
@ComponentScan(
        basePackages = "com.cheeseocean.im.common.core.business.mongo.impl",
        useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.cheeseocean\\.im\\.common\\.core\\.business\\.mongo\\.impl\\..*"
        )
)
public class CommonMongoPersistenceConfiguration {

    /**
     * Mongo 驱动的事务管理器。
     *
     * <p>事务是否启用由执行器决定：集群默认启用，all-in-one 默认降级，
     * 从而不要求本地联调 Mongo 必须以副本集形式启动。
     */
    @Bean
    @ConditionalOnMissingBean
    public MongoTransactionManager mongoTransactionManager(MongoDatabaseFactory databaseFactory) {
        return new MongoTransactionManager(databaseFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    public PersistenceTransactionExecutor persistenceTransactionExecutor(
            MongoTransactionManager mongoTransactionManager,
            Environment environment) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(mongoTransactionManager);
        return new MongoPersistenceTransactionExecutor(transactionTemplate, transactionsEnabled(environment));
    }

    private boolean transactionsEnabled(Environment environment) {
        Boolean configured = environment.getProperty("cheeseim.mongo.transactions-enabled", Boolean.class);
        if (configured != null) {
            return configured;
        }
        return "cluster".equalsIgnoreCase(environment.getProperty("cheeseim.runtime.mode"));
    }
}
