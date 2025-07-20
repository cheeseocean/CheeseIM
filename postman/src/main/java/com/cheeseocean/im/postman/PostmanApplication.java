package com.cheeseocean.im.postman;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Postman服务启动类
 * 1: 接收来自网关的消息发送至kafka
 * 2: 对消息的各种操作如删除、查询、撤回、已读回执等
 *
 * @author CheeseIM
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.cheeseocean.im.postman", "com.cheeseocean.im.common"})
@EnableKafka
@EnableDubbo
@EnableScheduling
public class PostmanApplication {

    public static void main(String[] args) {
        SpringApplication.run(PostmanApplication.class, args);
        System.out.println("🚀 CheeseIM Postman Started Successfully!");
        System.out.println("📨 Message service is running...");
        System.out.println("🔗 REST API: http://localhost:8082/api/v1/postman");
        System.out.println("💡 Health Check: http://localhost:8082/api/v1/postman/health");
    }
}
