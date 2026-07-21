package com.cheeseocean.im.apiserver;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Map;

/**
 * HTTP API 独立生产入口。
 *
 * <p>只扫描 HTTP adapter，自身不装配 Mongo、Kafka、Chronicle 或 RocksDB。
 * 领域操作均经 common-api Dubbo 契约调用对应服务。</p>
 */
@SpringBootApplication(scanBasePackages = "com.cheeseocean.im.apiserver")
@EnableDubbo
public class ApiServerApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(ApiServerApplication.class);
        application.setDefaultProperties(Map.of(
                "spring.config.name", "application-api-server",
                "cheeseim.runtime.mode", "cluster"));
        application.run(args);
    }
}
