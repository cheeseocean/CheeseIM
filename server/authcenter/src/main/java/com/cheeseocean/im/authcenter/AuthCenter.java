package com.cheeseocean.im.authcenter;

import com.cheeseocean.im.common.core.business.mongo.config.EnableCommonMongoPersistence;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Map;

@SpringBootApplication(scanBasePackages = {"com.cheeseocean.im.authcenter", "com.cheeseocean.im.common"})
@EnableDubbo
@EnableCommonMongoPersistence
public class AuthCenter {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(AuthCenter.class);
        application.setDefaultProperties(Map.of("spring.config.name", "application-authcenter"));
        application.run(args);
    }
}
