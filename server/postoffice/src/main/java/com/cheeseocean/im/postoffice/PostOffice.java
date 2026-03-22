package com.cheeseocean.im.postoffice;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Map;

/**
 * CheeseIM PostOffice 启动类
 *
 * @author xxxcrel
 */
@SpringBootApplication(scanBasePackages = {"com.cheeseocean.im.postoffice", "com.cheeseocean.im.common"})
@EnableDubbo
public class PostOffice {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(PostOffice.class);
        application.setDefaultProperties(Map.of("spring.config.name", "cheeseim-postoffice"));
        application.run(args);
    }
}
