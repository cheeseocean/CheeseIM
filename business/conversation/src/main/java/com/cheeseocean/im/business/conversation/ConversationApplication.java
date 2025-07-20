package com.cheeseocean.im.business.conversation;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * 会话服务启动类
 * 
 * @author CheeseIM
 */
@SpringBootApplication
@EnableDubbo
@EnableMongoRepositories(basePackages = "com.cheeseocean.im.business.conversation.repository")
public class ConversationApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(ConversationApplication.class, args);
    }
}
