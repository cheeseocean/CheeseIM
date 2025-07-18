package com.cheeseocean.im.message.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;

/**
 * MongoDB配置类
 * 
 * @author CheeseIM
 */
@Configuration
public class MongoConfig extends AbstractMongoClientConfiguration {
    
    @Override
    protected String getDatabaseName() {
        return "cheese_im";
    }
    
    @Override
    protected boolean autoIndexCreation() {
        return true; // 自动创建索引
    }
}
