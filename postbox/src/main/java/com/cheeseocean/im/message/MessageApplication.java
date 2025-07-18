package com.cheeseocean.im.message;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 消息服务启动类
 *
 * @author CheeseIM
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.cheeseocean.im.message", "com.cheeseocean.im.common"})
@EnableMongoRepositories(basePackages = "com.cheeseocean.im.message.repository")
@EnableKafka
@EnableDubbo
@EnableScheduling
public class MessageApplication {

    public static void main(String[] args) {
        SpringApplication.run(MessageApplication.class, args);
        System.out.println("CheeseIM Message Service Started Successfully!");
    }
}
