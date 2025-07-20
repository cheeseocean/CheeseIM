package com.cheeseocean.im.postoffice;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * CheeseIM PostOffice 启动类
 *
 * @author CheeseIM
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.cheeseocean.im.postoffice", "com.cheeseocean.im.common"})
@EnableDubbo
@EnableAsync
@EnableScheduling
public class PostOfficeApplication {

    public static void main(String[] args) {
        SpringApplication.run(PostOfficeApplication.class, args);
        System.out.println("CheeseIM PostOffice Started Successfully!");
        System.out.println("WebSocket Server is running on port 8080");
        System.out.println("Ready to accept client connections...");
    }
}
