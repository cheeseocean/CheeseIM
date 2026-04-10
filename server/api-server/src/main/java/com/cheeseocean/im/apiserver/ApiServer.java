package com.cheeseocean.im.apiserver;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Map;

/**
 * 统一的 HTTP 入口模块，只负责装配 REST 接口与相关适配层。
 */
@SpringBootApplication(scanBasePackages = {
        "com.cheeseocean.im.apiserver",
        "com.cheeseocean.im.authcenter",
        "com.cheeseocean.im.business",
        "com.cheeseocean.im.postbox",
        "com.cheeseocean.im.common"
})
@EnableDubbo
public class ApiServer {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(ApiServer.class);
        application.setDefaultProperties(Map.of("spring.config.name", "api-server"));
        application.run(args);
    }
}
