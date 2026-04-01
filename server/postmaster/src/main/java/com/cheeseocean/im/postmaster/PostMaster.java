package com.cheeseocean.im.postmaster;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

import java.util.Map;

/**
 * PostMaster服务启动类
 * 1: 接收来自网关的消息发送至kafka
 * 2: 消息持久化
 *
 * @author xxxcrel
 */
@SpringBootApplication(scanBasePackages = {"com.cheeseocean.im.common"})
@EnableKafka
@EnableDubbo
public class PostMaster {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(PostMaster.class);
        application.setDefaultProperties(Map.of("spring.config.name", "cheeseim-postmaster"));
        application.run(args);
    }
}
