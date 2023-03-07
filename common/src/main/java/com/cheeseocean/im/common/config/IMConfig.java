package com.cheeseocean.im.common.config;


import org.springframework.context.annotation.PropertySource;

@PropertySource(value = "classpath:config.yml", factory = YamlPropertySourceFactory.class)
public class IMConfig {

    Gateway gateway;

    public static class Gateway {
        int imPort;

        int wsPort;
    }

}
