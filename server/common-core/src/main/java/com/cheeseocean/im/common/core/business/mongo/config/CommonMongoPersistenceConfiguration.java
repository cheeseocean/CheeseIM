package com.cheeseocean.im.common.core.business.mongo.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

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
}
