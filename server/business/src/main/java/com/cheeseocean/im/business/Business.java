package com.cheeseocean.im.business;

import com.alicp.jetcache.anno.config.EnableMethodCache;
import com.cheeseocean.im.common.core.business.mongo.config.EnableCommonMongoPersistence;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Map;

@SpringBootApplication(scanBasePackages = {"com.cheeseocean.im.business", "com.cheeseocean.im.common"})
@EnableDubbo
@EnableCommonMongoPersistence
@EnableMethodCache(basePackages = "com.cheeseocean.im.business")
public class Business {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(Business.class);
        application.setDefaultProperties(Map.of("spring.config.name", "cheeseim-social"));
        application.run(args);
    }
}
