package com.cheeseocean.im.push;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

import java.util.Map;

/**
 * CheeseIM Push Service 启动类
 * 推送服务主应用程序入口
 * 
 * @author CheeseIM
 */
@SpringBootApplication(scanBasePackages = {"com.cheeseocean.im.push", "com.cheeseocean.im.common"})
@EnableKafka
@EnableDubbo
public class Push {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(Push.class);
        application.setDefaultProperties(Map.of("spring.config.name", "application-push"));
        application.run(args);
    }
}
