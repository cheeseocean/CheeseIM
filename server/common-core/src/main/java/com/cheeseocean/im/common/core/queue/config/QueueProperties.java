package com.cheeseocean.im.common.core.queue.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties(prefix = "app.queue")
public class QueueProperties {

    private String type = "chronicle";
    private String dataDir = "data/queue";
    private int pollIntervalMillis = 100;

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

    public Path topicDir(String topic) {
        return Path.of(dataDir).resolve(topic);
    }
}
