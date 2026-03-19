package com.cheeseocean.im.postoffice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * IM server transport configuration.
 */
@Configuration
@ConfigurationProperties(prefix = "cheeseim.postoffice")
public class IMServerConfig {

    private final TcpConfig tcp = new TcpConfig();
    private final WebSocketConfig websocket = new WebSocketConfig();
    private final SecurityConfig security = new SecurityConfig();

    public TcpConfig getTcp() {
        return tcp;
    }

    public WebSocketConfig getWebsocket() {
        return websocket;
    }

    public SecurityConfig getSecurity() {
        return security;
    }

    public static class SecurityConfig {
        private String jwtSecret;
        private long tokenExpiration;

        public String getJwtSecret() {
            return jwtSecret;
        }

        public void setJwtSecret(String jwtSecret) {
            this.jwtSecret = jwtSecret;
        }

        public long getTokenExpiration() {
            return tokenExpiration;
        }

        public void setTokenExpiration(long tokenExpiration) {
            this.tokenExpiration = tokenExpiration;
        }
    }

    public static class TcpConfig extends BaseTransportConfig {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class WebSocketConfig extends BaseTransportConfig {
        private boolean enabled = true;
        private String path = "/ws";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }

    public abstract static class BaseTransportConfig {
        private int port;
        private int bossThreads = 1;
        private int workerThreads = 0;
        private int maxConnections = 10000;
        private int idleTimeout = 300;
        private int heartbeatInterval = 30;
        private int receiveBufferSize = 65536;
        private int sendBufferSize = 65536;
        private boolean tcpNoDelay = true;
        private boolean keepAlive = true;
        private int backlog = 1024;
        private int maxFrameLength = 65536;
        private final SslConfig ssl = new SslConfig();

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public int getBossThreads() {
            return bossThreads;
        }

        public void setBossThreads(int bossThreads) {
            this.bossThreads = bossThreads;
        }

        public int getWorkerThreads() {
            return workerThreads;
        }

        public void setWorkerThreads(int workerThreads) {
            this.workerThreads = workerThreads;
        }

        public int getActualWorkerThreads() {
            return workerThreads <= 0
                ? Runtime.getRuntime().availableProcessors() * 2
                : workerThreads;
        }

        public int getMaxConnections() {
            return maxConnections;
        }

        public void setMaxConnections(int maxConnections) {
            this.maxConnections = maxConnections;
        }

        public int getIdleTimeout() {
            return idleTimeout;
        }

        public void setIdleTimeout(int idleTimeout) {
            this.idleTimeout = idleTimeout;
        }

        public int getHeartbeatInterval() {
            return heartbeatInterval;
        }

        public void setHeartbeatInterval(int heartbeatInterval) {
            this.heartbeatInterval = heartbeatInterval;
        }

        public int getReceiveBufferSize() {
            return receiveBufferSize;
        }

        public void setReceiveBufferSize(int receiveBufferSize) {
            this.receiveBufferSize = receiveBufferSize;
        }

        public int getSendBufferSize() {
            return sendBufferSize;
        }

        public void setSendBufferSize(int sendBufferSize) {
            this.sendBufferSize = sendBufferSize;
        }

        public boolean isTcpNoDelay() {
            return tcpNoDelay;
        }

        public void setTcpNoDelay(boolean tcpNoDelay) {
            this.tcpNoDelay = tcpNoDelay;
        }

        public boolean isKeepAlive() {
            return keepAlive;
        }

        public void setKeepAlive(boolean keepAlive) {
            this.keepAlive = keepAlive;
        }

        public int getBacklog() {
            return backlog;
        }

        public void setBacklog(int backlog) {
            this.backlog = backlog;
        }

        public int getMaxFrameLength() {
            return maxFrameLength;
        }

        public void setMaxFrameLength(int maxFrameLength) {
            this.maxFrameLength = maxFrameLength;
        }

        public SslConfig getSsl() {
            return ssl;
        }
    }

    public static class SslConfig {
        private boolean enabled;
        private String certPath = "";
        private String keyPath = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getCertPath() {
            return certPath;
        }

        public void setCertPath(String certPath) {
            this.certPath = certPath;
        }

        public String getKeyPath() {
            return keyPath;
        }

        public void setKeyPath(String keyPath) {
            this.keyPath = keyPath;
        }
    }
}
