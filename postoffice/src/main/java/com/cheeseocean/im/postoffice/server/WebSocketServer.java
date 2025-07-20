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
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;

/**
 * WebSocket服务器
 * 基于Netty实现的高性能WebSocket服务器
 * 
 * @author CheeseIM
 */
@Component
public class WebSocketServer implements CommandLineRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketServer.class);
    
    @Autowired
    private NettyConfig nettyConfig;
    
    @Autowired
    private WebSocketChannelInitializer channelInitializer;
    
    @Autowired
    private ConnectionManager connectionManager;
    
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ChannelFuture channelFuture;
    
    @Override
    public void run(String... args) throws Exception {
        start();
    }
    
    /**
     * 启动WebSocket服务器
     */
    public void start() throws Exception {
        logger.info("Starting WebSocket Server with config: {}", nettyConfig);
        
        // 初始化SSL上下文（如果启用SSL）
        channelInitializer.initSslContext();
        
        // 初始化连接管理器
        connectionManager.init();
        
        // 创建事件循环组
        bossGroup = new NioEventLoopGroup(nettyConfig.getBossThreads());
        workerGroup = new NioEventLoopGroup(nettyConfig.getActualWorkerThreads());
        
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(channelInitializer)
                    // 服务器选项
                    .option(ChannelOption.SO_BACKLOG, nettyConfig.getBacklog())
                    .option(ChannelOption.SO_REUSEADDR, true)
                    // 子Channel选项
                    .childOption(ChannelOption.TCP_NODELAY, nettyConfig.isTcpNoDelay())
                    .childOption(ChannelOption.SO_KEEPALIVE, nettyConfig.isKeepAlive())
                    .childOption(ChannelOption.SO_RCVBUF, nettyConfig.getReceiveBufferSize())
                    .childOption(ChannelOption.SO_SNDBUF, nettyConfig.getSendBufferSize());
            
            // 绑定端口并启动服务器
            channelFuture = bootstrap.bind(nettyConfig.getPort()).sync();
            
            logger.info("WebSocket Server started successfully on port {}", nettyConfig.getPort());
            logger.info("WebSocket endpoint: {}://localhost:{}{}", 
                       nettyConfig.isSslEnabled() ? "wss" : "ws", 
                       nettyConfig.getPort(), 
                       nettyConfig.getWebsocketPath());
            logger.info("Server configuration: bossThreads={}, workerThreads={}, maxConnections={}", 
                       nettyConfig.getBossThreads(), 
                       nettyConfig.getActualWorkerThreads(), 
                       nettyConfig.getMaxConnections());
            
            // 等待服务器关闭
            channelFuture.channel().closeFuture().sync();
            
        } catch (Exception e) {
            logger.error("Failed to start WebSocket Server", e);
            throw e;
        } finally {
            shutdown();
        }
    }
    
    /**
     * 停止WebSocket服务器
     */
    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down WebSocket Server...");
        
        try {
            // 关闭服务器Channel
            if (channelFuture != null) {
                channelFuture.channel().close().sync();
            }
            
            // 销毁连接管理器
            if (connectionManager != null) {
                connectionManager.destroy();
            }
            
        } catch (Exception e) {
            logger.error("Error during server shutdown", e);
        } finally {
            // 关闭事件循环组
            if (workerGroup != null) {
                workerGroup.shutdownGracefully();
            }
            if (bossGroup != null) {
                bossGroup.shutdownGracefully();
            }
        }
        
        logger.info("WebSocket Server shutdown completed");
    }
    
    /**
     * 检查服务器是否正在运行
     */
    public boolean isRunning() {
        return channelFuture != null && channelFuture.channel().isActive();
    }
    
    /**
     * 获取服务器状态信息
     */
    public ServerStatus getStatus() {
        ServerStatus status = new ServerStatus();
        status.setRunning(isRunning());
        status.setPort(nettyConfig.getPort());
        status.setSslEnabled(nettyConfig.isSslEnabled());
        status.setWebsocketPath(nettyConfig.getWebsocketPath());
        
        if (connectionManager != null) {
            status.setTotalConnections(connectionManager.getTotalConnectionCount());
            status.setOnlineUsers(connectionManager.getOnlineUserCount());
        }
        
        return status;
    }
    
    /**
     * 服务器状态信息类
     */
    public static class ServerStatus {
        private boolean running;
        private int port;
        private boolean sslEnabled;
        private String websocketPath;
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
        
        public boolean isSslEnabled() {
            return sslEnabled;
        }
        
        public void setSslEnabled(boolean sslEnabled) {
            this.sslEnabled = sslEnabled;
        }
        
        public String getWebsocketPath() {
            return websocketPath;
        }
        
        public void setWebsocketPath(String websocketPath) {
            this.websocketPath = websocketPath;
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
                    ", sslEnabled=" + sslEnabled +
                    ", websocketPath='" + websocketPath + '\'' +
                    ", totalConnections=" + totalConnections +
                    ", onlineUsers=" + onlineUsers +
                    '}';
        }
    }
}
