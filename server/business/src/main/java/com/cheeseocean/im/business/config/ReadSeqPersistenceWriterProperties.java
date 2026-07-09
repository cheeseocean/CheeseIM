package com.cheeseocean.im.business.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * readSeq 写后缓冲配置。
 */
@Component
@ConfigurationProperties(prefix = "cheeseim.business.read-seq-writer")
public class ReadSeqPersistenceWriterProperties {

    private int workerCount = 4;
    private int queueCapacityPerWorker = 1000;

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
