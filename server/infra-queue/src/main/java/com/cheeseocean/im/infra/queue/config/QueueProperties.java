package com.cheeseocean.im.infra.queue.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "cheeseim.queue")
public class QueueProperties {

    private String type = "chronicle";
    private String dataDir = "data/queue";
    private int pollIntervalMillis = 100;
    private Consumer consumer = new Consumer();
    private Map<String, Listener> listeners = new HashMap<>();

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public int getPollIntervalMillis() {
        return pollIntervalMillis;
    }

    public void setPollIntervalMillis(int pollIntervalMillis) {
        this.pollIntervalMillis = pollIntervalMillis;
    }

    public Consumer getConsumer() {
        return consumer;
    }

    public void setConsumer(Consumer consumer) {
        this.consumer = consumer == null ? new Consumer() : consumer;
    }

    public Map<String, Listener> getListeners() {
        return listeners;
    }

    public void setListeners(Map<String, Listener> listeners) {
        this.listeners = listeners == null ? new HashMap<>() : new HashMap<>(listeners);
    }

    /**
     * 以稳定 consumer group 作为运维配置键；未配置项回退到注解声明的业务默认值。
     */
    public ListenerSettings resolveListener(String group,
                                            int defaultConcurrency,
                                            int defaultBatchSize,
                                            long defaultBatchIntervalMillis) {
        Listener listener = listeners.get(group);
        return new ListenerSettings(
                positive(listener == null ? null : listener.getConcurrency(), defaultConcurrency),
                positive(listener == null ? null : listener.getBatchSize(), defaultBatchSize),
                positive(listener == null ? null : listener.getBatchIntervalMillis(), defaultBatchIntervalMillis));
    }

    public Path topicDir(String topic) {
        return Path.of(dataDir).resolve(topic);
    }

    private static int positive(Integer configured, int fallback) {
        return configured == null ? Math.max(1, fallback) : Math.max(1, configured);
    }

    private static long positive(Long configured, long fallback) {
        return configured == null ? Math.max(1L, fallback) : Math.max(1L, configured);
    }

    /** 两种队列后端共享的消费失败策略。maxAttempts 包含首次执行。 */
    public static class Consumer {
        private int maxAttempts = 3;
        private long retryIntervalMillis = 1_000L;

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = Math.max(1, maxAttempts);
        }

        public long getRetryIntervalMillis() {
            return retryIntervalMillis;
        }

        public void setRetryIntervalMillis(long retryIntervalMillis) {
            this.retryIntervalMillis = Math.max(0L, retryIntervalMillis);
        }
    }

    /** 单个稳定 consumer group 的吞吐参数；group 身份本身禁止由环境变量漂移。 */
    public static class Listener {
        private Integer concurrency;
        private Integer batchSize;
        private Long batchIntervalMillis;

        public Integer getConcurrency() {
            return concurrency;
        }

        public void setConcurrency(Integer concurrency) {
            this.concurrency = concurrency;
        }

        public Integer getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(Integer batchSize) {
            this.batchSize = batchSize;
        }

        public Long getBatchIntervalMillis() {
            return batchIntervalMillis;
        }

        public void setBatchIntervalMillis(Long batchIntervalMillis) {
            this.batchIntervalMillis = batchIntervalMillis;
        }
    }

    public record ListenerSettings(int concurrency, int batchSize, long batchIntervalMillis) {
    }
}
