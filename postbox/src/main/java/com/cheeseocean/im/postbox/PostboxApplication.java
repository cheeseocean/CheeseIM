package com.cheeseocean.im.postbox;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * CheeseIM Postbox 启动类
 * 1: 负责消息路由、分发
 * 2: 消息持久化
 *
 * @author xxxcrel
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.cheeseocean.im.postbox", "com.cheeseocean.im.common"})
@EnableKafka
@EnableDubbo
@EnableMongoRepositories
public class PostboxApplication {

    public static void main(String[] args) {
        SpringApplication.run(PostboxApplication.class, args);
        System.out.println("🚀 CheeseIM Postbox Started Successfully!");
        System.out.println("📨 Message routing and transfer service is running...");
        System.out.println("🔗 REST API: http://localhost:8082/api/v1/postbox");
        System.out.println("💡 Health Check: http://localhost:8082/api/v1/postbox/health");
    }
}
