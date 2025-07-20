package com.cheeseocean.im.postoffice.server;

import com.cheeseocean.im.postoffice.config.NettyConfig;
import com.cheeseocean.im.postoffice.connection.ConnectionManager;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;

/**
 * TCP服务器
 * 基于Netty实现的高性能TCP服务器，使用自定义二进制协议
 * 
 * @author CheeseIM
 */
@Component
@Order(2) // 在WebSocketServer之后启动
public class TcpServer implements CommandLineRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(TcpServer.class);
    
    @Autowired
    private NettyConfig nettyConfig;
    
    @Autowired
    private TcpChannelInitializer channelInitializer;
    
    @Autowired
    private ConnectionManager connectionManager;
    
    @Value("${postoffice.tcp.port:8081}")
    private int tcpPort;
    
    @Value("${postoffice.tcp.enabled:true}")
    private boolean tcpEnabled;
    
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ChannelFuture channelFuture;
    
    @Override
    public void run(String... args) throws Exception {
        if (tcpEnabled) {
            start();
        } else {
            logger.info("TCP Server is disabled");
        }
    }
    
    /**
     * 启动TCP服务器
     */
    public void start() throws Exception {
        logger.info("Starting TCP Server on port: {}", tcpPort);
        
        // 创建事件循环组
        bossGroup = new NioEventLoopGroup(nettyConfig.getBossThreads());
        workerGroup = new NioEventLoopGroup(nettyConfig.getActualWorkerThreads());
        
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(channelInitializer)
                    .option(ChannelOption.SO_BACKLOG, nettyConfig.getBacklog())
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_RCVBUF, nettyConfig.getReceiveBufferSize())
                    .childOption(ChannelOption.SO_SNDBUF, nettyConfig.getSendBufferSize());
            
            // 绑定端口并启动服务器
            channelFuture = bootstrap.bind(tcpPort).sync();
            
            logger.info("TCP Server started successfully on port: {}", tcpPort);
            logger.info("TCP Server configuration: bossThreads={}, workerThreads={}, backlog={}", 
                       nettyConfig.getBossThreads(), 
                       nettyConfig.getActualWorkerThreads(), 
                       nettyConfig.getBacklog());
            
        } catch (Exception e) {
            logger.error("Failed to start TCP Server on port: {}", tcpPort, e);
            shutdown();
            throw e;
        }
    }
    
    /**
     * 关闭TCP服务器
     */
    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down TCP Server...");
        
        try {
            if (channelFuture != null) {
                channelFuture.channel().close().sync();
            }
        } catch (InterruptedException e) {
            logger.warn("Interrupted while closing TCP server channel", e);
            Thread.currentThread().interrupt();
        }
        
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        
        logger.info("TCP Server shutdown completed");
    }
    
    /**
     * 获取服务器状态
     */
    public ServerStatus getStatus() {
        ServerStatus status = new ServerStatus();
        status.setRunning(channelFuture != null && channelFuture.channel().isActive());
        status.setPort(tcpPort);
        status.setProtocol("TCP");
        status.setBossThreads(nettyConfig.getBossThreads());
        status.setWorkerThreads(nettyConfig.getActualWorkerThreads());
        status.setStartTime(System.currentTimeMillis()); // 这里应该记录实际启动时间
        
        if (connectionManager != null) {
            status.setTotalConnections(connectionManager.getTotalConnectionCount());
            status.setOnlineUsers(connectionManager.getOnlineUserCount());
        }
        
        return status;
    }
    
    /**
     * 服务器状态类
     */
    public static class ServerStatus {
        private boolean running;
        private int port;
        private String protocol;
        private int bossThreads;
        private int workerThreads;
        private long startTime;
        private long totalConnections;
        private long onlineUsers;
        
        // Getter and Setter methods
        public boolean isRunning() {
            return running;
        }
        
        public void setRunning(boolean running) {
            this.running = running;
        }
        
        public int getPort() {
            return port;
        }
        
        public void setPort(int port) {
            this.port = port;
        }
        
        public String getProtocol() {
            return protocol;
        }
        
        public void setProtocol(String protocol) {
            this.protocol = protocol;
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
        
        public long getStartTime() {
            return startTime;
        }
        
        public void setStartTime(long startTime) {
            this.startTime = startTime;
        }
        
        public long getTotalConnections() {
            return totalConnections;
        }
        
        public void setTotalConnections(long totalConnections) {
            this.totalConnections = totalConnections;
        }
        
        public long getOnlineUsers() {
            return onlineUsers;
        }
        
        public void setOnlineUsers(long onlineUsers) {
            this.onlineUsers = onlineUsers;
        }
        
        @Override
        public String toString() {
            return "ServerStatus{" +
                    "running=" + running +
                    ", port=" + port +
                    ", protocol='" + protocol + '\'' +
                    ", bossThreads=" + bossThreads +
                    ", workerThreads=" + workerThreads +
                    ", startTime=" + startTime +
                    ", totalConnections=" + totalConnections +
                    ", onlineUsers=" + onlineUsers +
                    '}';
        }
    }
}
