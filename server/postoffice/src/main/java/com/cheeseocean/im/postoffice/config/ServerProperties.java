package com.cheeseocean.im.postoffice.config;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * IM server transport configuration.
 *
 * @author xxxcrel
 */
@Configuration
@ConfigurationProperties(prefix = "cheeseim.postoffice")
@Data
public class ServerProperties {

    private final TcpConfig        tcp        = new TcpConfig();
    private final WebSocketConfig  websocket  = new WebSocketConfig();
    private final SecurityConfig   security   = new SecurityConfig();
    private final ConnectionConfig connection = new ConnectionConfig();

    @Data
    public static class ConnectionConfig {
        private String multiLoginStrategy;
        private long   timeoutMs;
        private int    maxConnectionsPerUser;
    }

    @Data
    public static class SecurityConfig {
        private String jwtSecret;
        private long   tokenExpiration;

    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class TcpConfig extends BaseTransportConfig {
        private boolean enabled = true;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class WebSocketConfig extends BaseTransportConfig {
        private boolean enabled = true;
        private String  path    = "/ws";

    }

    @Data
    public abstract static class BaseTransportConfig {
        private final SslConfig ssl               = new SslConfig();
        private       int       port;
        private       int       bossThreads       = 1;
        private       int       workerThreads     = 0;
        private       int       maxConnections    = 10000;
        private       int       idleTimeout       = 300;
        private       int       heartbeatInterval = 30;
        private       int       receiveBufferSize = 65536;
        private       int       sendBufferSize    = 65536;
        private       boolean   tcpNoDelay        = true;
        private       boolean   keepAlive         = true;
        private       int       backlog           = 1024;
        private       int       maxFrameLength    = 65536;

        public int getActualWorkerThreads() {
            return workerThreads <= 0
                    ? Runtime.getRuntime().availableProcessors() * 2
                    : workerThreads;
        }
    }

    @Data
    public static class SslConfig {
        private boolean enabled;
        private String  certPath = "";
        private String  keyPath  = "";
    }
}
