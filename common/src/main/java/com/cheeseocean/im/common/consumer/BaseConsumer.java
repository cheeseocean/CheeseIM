package com.cheeseocean.im.common.consumer;

import com.cheeseocean.im.common.config.IMConfig;

public interface BaseConsumer {

    /**
     * init consumer
     * @param IMConfig
     */
    void init(IMConfig IMConfig);

}
