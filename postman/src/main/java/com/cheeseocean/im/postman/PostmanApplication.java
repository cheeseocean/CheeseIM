package com.cheeseocean.im.postman;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * 消息投递服务启动类 (对应open-im-server的msgtransfer)
 * 
 * @author CheeseIM
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.cheeseocean.im.postman", "com.cheeseocean.im.common"})
public class PostmanApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(PostmanApplication.class, args);
    }
}
