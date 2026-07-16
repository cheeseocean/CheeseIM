package com.cheeseocean.im.postman.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 离线厂商推送有界线程池与分批背压配置。 */
@Data
@ConfigurationProperties(prefix = "cheeseim.push.executor")
public class OfflinePushExecutorProperties {
    private int corePoolSize = 8;
    private int maxPoolSize = 32;
    private int queueCapacity = 2_000;
    private int batchSize = 500;
}
