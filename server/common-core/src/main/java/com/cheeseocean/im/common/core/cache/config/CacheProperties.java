package com.cheeseocean.im.common.core.cache.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties(prefix = "app.cache")
public class CacheProperties {

    private String dataDir = "data/cache";
    private long l1MaximumSize = 10_000L;

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public long getL1MaximumSize() {
        return l1MaximumSize;
    }

    public void setL1MaximumSize(long l1MaximumSize) {
        this.l1MaximumSize = l1MaximumSize;
    }

    public Path resolve(String child) {
        return Path.of(dataDir).resolve(child);
    }
}
