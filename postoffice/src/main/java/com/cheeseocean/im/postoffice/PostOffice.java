package com.cheeseocean.im.postoffice;

import com.cheeseocean.im.common.ApplicationKeeper;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 消息网关（负责将用户发送的消息送至消息代理服务中（Kafka，File，DB）
 *
 * @author xxxcrel
 * @date 2023/05/24
 */
@Configuration
@ComponentScan(basePackages = "com.cheeseocean.im.postoffice")
@EnableDubbo
public class PostOffice {

    public static void main(String[] args) {
        new ApplicationKeeper(new AnnotationConfigApplicationContext(PostOffice.class)).keep();
    }
}
