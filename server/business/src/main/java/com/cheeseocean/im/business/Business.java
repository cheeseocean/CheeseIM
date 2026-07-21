package com.cheeseocean.im.business;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Map;

@SpringBootApplication(scanBasePackages = {"com.cheeseocean.im.business", "com.cheeseocean.im.common"})
@EnableDubbo
public class Business {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(Business.class);
        application.setDefaultProperties(Map.of("spring.config.name", "application-business"));
        application.run(args);
    }
}
