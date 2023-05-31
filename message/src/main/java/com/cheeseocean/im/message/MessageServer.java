package com.cheeseocean.im.message;

import com.cheeseocean.im.common.ApplicationKeeper;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 消息组件
 * 1. 负责将消息落盘(Kafka, File, DB)
 *
 * @author xxxcrel
 * Created on 2023/4/17
 */
@Slf4j
@Configuration
@ComponentScan(basePackages = "com.cheeseocean.im.message")
@EnableDubbo(scanBasePackages = "com.cheeseocean.im.message.service")
public class MessageServer {

    public static void main(String[] args) {
        new ApplicationKeeper(new AnnotationConfigApplicationContext(MessageServer.class)).keep();
    }
}