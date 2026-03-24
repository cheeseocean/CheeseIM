package com.cheeseocean.im.common.core.store.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties(prefix = "app.state")
public class StateStoreProperties {

    private String dataDir = "data/state";
    private int sequenceRangeSize = 100;

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public int getSequenceRangeSize() {
        return sequenceRangeSize;
    }

    public void setSequenceRangeSize(int sequenceRangeSize) {
        this.sequenceRangeSize = sequenceRangeSize;
    }

    public Path resolve(String child) {
        return Path.of(dataDir).resolve(child);
    }
}
