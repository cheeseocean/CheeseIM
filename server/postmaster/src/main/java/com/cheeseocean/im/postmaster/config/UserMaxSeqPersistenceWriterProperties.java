package com.cheeseocean.im.postmaster.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * user maxSeq 写后缓冲配置。
 */
@Component
@ConfigurationProperties(prefix = "cheeseim.postmaster.user-max-seq-writer")
public class UserMaxSeqPersistenceWriterProperties {

    private int workerCount = 4;
    private int queueCapacityPerWorker = 2000;

    public int getWorkerCount() {
        return workerCount;
    }

    public void setWorkerCount(int workerCount) {
        this.workerCount = workerCount;
    }

    public int getQueueCapacityPerWorker() {
        return queueCapacityPerWorker;
    }

    public void setQueueCapacityPerWorker(int queueCapacityPerWorker) {
        this.queueCapacityPerWorker = queueCapacityPerWorker;
    }
}
