package com.cheeseocean.im.postoffice.server;

import com.cheeseocean.im.postoffice.config.IMServerConfig;
import com.cheeseocean.im.postoffice.connection.ConnectionManager;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import com.cheeseocean.im.common.core.logging.CommonLoggers;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;

/**
 * WebSocket服务器
 * 基于Netty实现的高性能WebSocket服务器
 * 
 * @author xxxcrel
 */
@Component
public class WebSocketServer implements CommandLineRunner {
    
    private static final Logger logger = CommonLoggers.POSTOFFICE;
    
    @Autowired
    private IMServerConfig IMServerConfig;
    
    @Autowired
    private WebSocketChannelInitializer channelInitializer;
    
    @Autowired
    private ConnectionManager connectionManager;

    private IMServerConfig.WebSocketConfig websocketConfig;
    
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ChannelFuture channelFuture;
    
    @Override
    public void run(String... args) throws Exception {
        websocketConfig = IMServerConfig.getWebsocket();
        if (websocketConfig.isEnabled()) {
            start();
        } else {
            logger.info("WebSocket Server is disabled");
        }
    }
    
    /**
     * 启动WebSocket服务器
     */
    public void start() throws Exception {
        logger.info("Starting WebSocket Server with config: {}", websocketConfig);
        
        // 初始化SSL上下文（如果启用SSL）
        channelInitializer.initSslContext();
        
        // 初始化连接管理器
        connectionManager.init();
        
        // 创建事件循环组
        bossGroup = new NioEventLoopGroup(websocketConfig.getBossThreads());
        workerGroup = new NioEventLoopGroup(websocketConfig.getActualWorkerThreads());
        
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(channelInitializer)
                    // 服务器选项
                    .option(ChannelOption.SO_BACKLOG, websocketConfig.getBacklog())
                    .option(ChannelOption.SO_REUSEADDR, true)
                    // 子Channel选项
                    .childOption(ChannelOption.TCP_NODELAY, websocketConfig.isTcpNoDelay())
                    .childOption(ChannelOption.SO_KEEPALIVE, websocketConfig.isKeepAlive())
                    .childOption(ChannelOption.SO_RCVBUF, websocketConfig.getReceiveBufferSize())
                    .childOption(ChannelOption.SO_SNDBUF, websocketConfig.getSendBufferSize());
            
            // 绑定端口并启动服务器
            channelFuture = bootstrap.bind(websocketConfig.getPort()).sync();
            
            logger.info("WebSocket Server started successfully on port {}", websocketConfig.getPort());
            logger.info("WebSocket endpoint: {}://localhost:{}{}", 
                       websocketConfig.getSsl().isEnabled() ? "wss" : "ws",
                       websocketConfig.getPort(),
                       websocketConfig.getPath());
            logger.info("Server configuration: bossThreads={}, workerThreads={}, maxConnections={}",
                       websocketConfig.getBossThreads(),
                       websocketConfig.getActualWorkerThreads(),
                       websocketConfig.getMaxConnections());

        } catch (Exception e) {
            logger.error("Failed to start WebSocket Server", e);
            shutdown();
            throw e;
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
        IMServerConfig.WebSocketConfig currentConfig =
                websocketConfig != null ? websocketConfig : IMServerConfig.getWebsocket();
        status.setPort(currentConfig.getPort());
        status.setSslEnabled(currentConfig.getSsl().isEnabled());
        status.setWebsocketPath(currentConfig.getPath());
        
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
