package com.cheeseocean.im.common.config;


import org.springframework.context.annotation.PropertySource;

@PropertySource(value = "classpath:config.yml", factory = YamlPropertySourceFactory.class)
public class IMConfig {

    Gateway gateway;

    public Gateway getGateway() {
        return gateway;
    }

    public void setGateway(Gateway gateway) {
        this.gateway = gateway;
    }

    public static class Gateway {
        int imPort;

        int wsPort;

        public int getImPort() {
            return imPort;
        }

        public void setImPort(int imPort) {
            this.imPort = imPort;
        }

        public int getWsPort() {
            return wsPort;
        }

        public void setWsPort(int wsPort) {
            this.wsPort = wsPort;
        }
    }

}
