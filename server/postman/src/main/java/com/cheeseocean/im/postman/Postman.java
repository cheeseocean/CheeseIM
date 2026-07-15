package com.cheeseocean.im.postman;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Map;

/**
 * CheeseIM Push Service 启动类
 * 推送服务主应用程序入口
 * 
 * @author xxxcrel
 */
@SpringBootApplication(scanBasePackages = {"com.cheeseocean.im.postman", "com.cheeseocean.im.common"})
@EnableDubbo
public class Postman {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(Postman.class);
        application.setDefaultProperties(Map.of("spring.config.name", "application-postman"));
        application.run(args);
    }
}
