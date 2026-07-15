package com.cheeseocean.im.postbox;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Map;

/**
 * CheeseIM Postbox 启动类
 * 1: 负责消息路由、分发
 * 2: 消息持久化
 *
 * @author xxxcrel
 */
@SpringBootApplication(scanBasePackages = {"com.cheeseocean.im.postbox", "com.cheeseocean.im.common"})
@EnableDubbo
public class Postbox {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(Postbox.class);
        application.setDefaultProperties(Map.of("spring.config.name", "application-postbox"));
        application.run(args);
    }
}
