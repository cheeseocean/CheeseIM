package com.cheeseocean.im.common.config;


import org.springframework.context.annotation.PropertySource;

@PropertySource(value = "classpath:gateway.yml", factory = YamlPropertySourceFactory.class)
public class IMConfig {

    int gatewayPort;

    int websocketPort;

    public int getGatewayPort() {
        return gatewayPort;
    }

    public void setGatewayPort(int gatewayPort) {
        this.gatewayPort = gatewayPort;
    }

    public int getWebsocketPort() {
        return websocketPort;
    }

    public void setWebsocketPort(int websocketPort) {
        this.websocketPort = websocketPort;
    }
}
