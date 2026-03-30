package com.cheeseocean.im.postoffice.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerPropertiesTest {

    @Test
    void actualWorkerThreadsShouldDefaultToTwiceAvailableProcessors() {
        ServerProperties.TcpConfig config = new ServerProperties.TcpConfig();

        config.setWorkerThreads(0);

        assertEquals(Runtime.getRuntime().availableProcessors() * 2, config.getActualWorkerThreads());
    }

    @Test
    void actualWorkerThreadsShouldRespectExplicitValue() {
        ServerProperties.WebSocketConfig config = new ServerProperties.WebSocketConfig();

        config.setWorkerThreads(12);

        assertEquals(12, config.getActualWorkerThreads());
    }
}
