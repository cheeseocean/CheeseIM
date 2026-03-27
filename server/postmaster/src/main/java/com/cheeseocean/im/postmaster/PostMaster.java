package com.cheeseocean.im.postmaster;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Map;

/**
 * PostMaster服务启动类
 * 1: 接收来自网关的消息发送至kafka
 * 2: 对消息的各种操作如删除、查询、撤回、已读回执等
 *
 * @author xxxcrel
 */
@SpringBootApplication(scanBasePackages = {"com.cheeseocean.im.postmaster", "com.cheeseocean.im.common"})
@EnableKafka
@EnableDubbo
@EnableScheduling
public class PostMaster {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(PostMaster.class);
        application.setDefaultProperties(Map.of("spring.config.name", "cheeseim-postmaster"));
        application.run(args);
    }
}
