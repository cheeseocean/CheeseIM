package com.cheeseocean.im.common.core.store.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties(prefix = "cheeseim.state")
public class StateStoreProperties {

    private String dataDir = "data/state";

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public Path resolve(String child) {
        return Path.of(dataDir).resolve(child);
    }
}
