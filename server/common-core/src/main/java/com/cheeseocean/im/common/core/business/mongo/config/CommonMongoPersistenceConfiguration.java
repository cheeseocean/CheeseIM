package com.cheeseocean.im.common.core.business.mongo.config;

import com.cheeseocean.im.common.core.business.transaction.MongoPersistenceTransactionExecutor;
import com.cheeseocean.im.common.core.business.transaction.PersistenceTransactionExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Shared Mongo persistence registration for common-core owned business data.
 */
@Configuration
@EnableMongoRepositories(basePackages = "com.cheeseocean.im.common.core.business.mongo.repository")
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
