package com.cheeseocean.im.message;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 消息服务启动类
 *
 * @author CheeseIM
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.cheeseocean.im.message", "com.cheeseocean.im.common"})
@EnableKafka
@EnableDubbo
@EnableScheduling
public class PostboxApplication {

    public static void main(String[] args) {
        SpringApplication.run(PostboxApplication.class, args);
        System.out.println("CheeseIM Postbox Service Started Successfully!");
    }
}
