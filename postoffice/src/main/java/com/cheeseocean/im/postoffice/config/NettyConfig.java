package com.cheeseocean.im.postoffice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Netty配置类
 * 
 * @author CheeseIM
 */
@Configuration
@ConfigurationProperties(prefix = "cheese.im.server")
public class NettyConfig {
    
    /**
     * 服务器端口
     */
    private int port = 8080;
    
    /**
     * Boss线程数
     */
    private int bossThreads = 1;
    
    /**
     * Worker线程数，0表示使用CPU核数*2
     */
    private int workerThreads = 0;
    
    /**
     * 最大连接数
     */
    private int maxConnections = 10000;
    
    /**
     * 连接空闲超时时间（秒）
     */
    private int idleTimeout = 300;
    
    /**
     * 心跳间隔时间（秒）
     */
    private int heartbeatInterval = 30;
    
    /**
     * WebSocket路径
     */
    private String websocketPath = "/ws";
    
    /**
     * 是否启用SSL
     */
    private boolean sslEnabled = false;
    
    /**
     * SSL证书路径
     */
    private String sslCertPath;
    
    /**
     * SSL私钥路径
     */
    private String sslKeyPath;
    
    /**
     * 接收缓冲区大小
     */
    private int receiveBufferSize = 65536;
    
    /**
     * 发送缓冲区大小
     */
    private int sendBufferSize = 65536;
    
    /**
     * 是否启用TCP_NODELAY
     */
    private boolean tcpNoDelay = true;
    
    /**
     * 是否启用SO_KEEPALIVE
     */
    private boolean keepAlive = true;
    
    /**
     * SO_BACKLOG大小
     */
    private int backlog = 1024;
    
    /**
     * 最大帧长度
     */
    private int maxFrameLength = 65536;
    
    // Getter and Setter methods
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
    
    public String getWebsocketPath() {
        return websocketPath;
    }
    
    public void setWebsocketPath(String websocketPath) {
        this.websocketPath = websocketPath;
    }
    
    public boolean isSslEnabled() {
        return sslEnabled;
    }
    
    public void setSslEnabled(boolean sslEnabled) {
        this.sslEnabled = sslEnabled;
    }
    
    public String getSslCertPath() {
        return sslCertPath;
    }
    
    public void setSslCertPath(String sslCertPath) {
        this.sslCertPath = sslCertPath;
    }
    
    public String getSslKeyPath() {
        return sslKeyPath;
    }
    
    public void setSslKeyPath(String sslKeyPath) {
        this.sslKeyPath = sslKeyPath;
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
    
    /**
     * 获取实际的Worker线程数
     */
    public int getActualWorkerThreads() {
        return workerThreads <= 0 ? Runtime.getRuntime().availableProcessors() * 2 : workerThreads;
    }
    
    @Override
    public String toString() {
        return "NettyConfig{" +
                "port=" + port +
                ", bossThreads=" + bossThreads +
                ", workerThreads=" + workerThreads +
                ", maxConnections=" + maxConnections +
                ", idleTimeout=" + idleTimeout +
                ", heartbeatInterval=" + heartbeatInterval +
                ", websocketPath='" + websocketPath + '\'' +
                ", sslEnabled=" + sslEnabled +
                ", receiveBufferSize=" + receiveBufferSize +
                ", sendBufferSize=" + sendBufferSize +
                ", tcpNoDelay=" + tcpNoDelay +
                ", keepAlive=" + keepAlive +
                ", backlog=" + backlog +
                ", maxFrameLength=" + maxFrameLength +
                '}';
    }
}
