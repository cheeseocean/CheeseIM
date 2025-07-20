package com.cheeseocean.im.business.conversation.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;

/**
 * 会话模块配置类
 * 
 * @author CheeseIM
 */
@Configuration
public class ConversationConfig extends AbstractMongoClientConfiguration {
    
    @Override
    protected String getDatabaseName() {
        return "cheese_im";
    }
    
    @Override
    protected boolean autoIndexCreation() {
        return true;
    }
}
