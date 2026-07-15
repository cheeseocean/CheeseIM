package com.cheeseocean.im.postmaster;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

import java.util.Map;

/**
 * PostMaster服务启动类
 * 接收 ingress 事件、编排消息持久化并发布 delivery 事件。
 *
 * @author xxxcrel
 */
@SpringBootApplication(scanBasePackages = {"com.cheeseocean.im.postmaster", "com.cheeseocean.im.common"})
@EnableDubbo
public class PostMaster {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(PostMaster.class);
        application.setDefaultProperties(Map.of("spring.config.name", "application-postmaster"));
        application.run(args);
    }
}
