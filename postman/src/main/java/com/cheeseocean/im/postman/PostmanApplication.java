package com.cheeseocean.im.postman;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * CheeseIM Postman Message Transfer 启动类
 * 参照OpenIM Server的msgtransfer实现
 * 负责消息路由、分发和传输
 *
 * @author CheeseIM
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.cheeseocean.im.postman", "com.cheeseocean.im.common"})
@EnableKafka
@EnableAsync
@EnableScheduling
public class PostmanApplication {

    public static void main(String[] args) {
        SpringApplication.run(PostmanApplication.class, args);
        System.out.println("🚀 CheeseIM Postman Message Transfer Started Successfully!");
        System.out.println("📨 Message routing and transfer service is running...");
        System.out.println("🔗 REST API: http://localhost:8082/api/v1/postman");
        System.out.println("💡 Health Check: http://localhost:8082/api/v1/postman/health");
    }
}
