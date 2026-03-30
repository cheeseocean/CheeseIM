package com.cheeseocean.im.postoffice.server;

import com.cheeseocean.im.common.core.logging.CommonLoggers;
import com.cheeseocean.im.postoffice.codec.TcpEnvelopeDecoder;
import com.cheeseocean.im.postoffice.codec.TcpEnvelopeEncoder;
import com.cheeseocean.im.postoffice.config.ServerProperties;
import com.cheeseocean.im.postoffice.connection.ConnectionManager;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;

/**
 * TCP服务器
 * 基于Netty实现的高性能TCP服务器，使用自定义二进制协议
 *
 * @author xxxcrel
 */
@Component
@Order(2) // 在WebSocketServer之后启动
public class TcpServer implements CommandLineRunner, Server {

    private static final Logger logger = CommonLoggers.POSTOFFICE;

    @Autowired
    private ServerProperties           ServerProperties;
    @Autowired
    private ConnectionManager          connectionManager;
    @Autowired
    private TcpServerHandler           tcpServerHandler;
    /**
     * TCP服务相关配置
     */
    private ServerProperties.TcpConfig tcpConfig;
    private EventLoopGroup             bossGroup;
    private EventLoopGroup           workerGroup;
    private ChannelFuture            channelFuture;
    private long                     startTime;

    @Override
    public void run(String... args) throws Exception {
        tcpConfig = ServerProperties.getTcp();
        if (tcpConfig.isEnabled()) {
            start();
        } else {
            logger.info("TCP Server is disabled");
        }
    }

    /**
     * 启动TCP服务器
     */
    public void start() throws Exception {
        logger.info("Starting TCP Server on port: {}", tcpConfig.getPort());

        TcpChannelInitializer channelInitializer = new TcpChannelInitializer();
        channelInitializer.initSslContext();

        // 创建事件循环组
        bossGroup = new NioEventLoopGroup(tcpConfig.getBossThreads());
        workerGroup = new NioEventLoopGroup(tcpConfig.getActualWorkerThreads());

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(channelInitializer)
                    .option(ChannelOption.SO_BACKLOG, tcpConfig.getBacklog())
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, tcpConfig.isKeepAlive())
                    .childOption(ChannelOption.TCP_NODELAY, tcpConfig.isTcpNoDelay())
                    .childOption(ChannelOption.SO_RCVBUF, tcpConfig.getReceiveBufferSize())
                    .childOption(ChannelOption.SO_SNDBUF, tcpConfig.getSendBufferSize());

            startTime = System.currentTimeMillis();
            // 绑定端口并启动服务器
            channelFuture = bootstrap.bind(tcpConfig.getPort()).sync();

            logger.info("TCP Server started successfully on port: {}", tcpConfig.getPort());
            logger.info("TCP Server configuration: bossThreads={}, workerThreads={}, backlog={}",
                    tcpConfig.getBossThreads(),
                    tcpConfig.getActualWorkerThreads(),
                    tcpConfig.getBacklog());

        } catch (Exception e) {
            logger.error("Failed to start TCP Server on port: {}", tcpConfig.getPort(), e);
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
        ServerProperties.TcpConfig currentConfig = tcpConfig != null ? tcpConfig : ServerProperties.getTcp();
        ServerStatus               status        = new ServerStatus();
        status.setRunning(channelFuture != null && channelFuture.channel().isActive());
        status.setPort(currentConfig.getPort());
        status.setProtocol("TCP");
        status.setBossThreads(currentConfig.getBossThreads());
        status.setWorkerThreads(currentConfig.getActualWorkerThreads());
        status.setStartTime(startTime);

        if (connectionManager != null) {
            status.setTotalConnections(connectionManager.getTotalConnectionCount());
            status.setOnlineUsers(connectionManager.getOnlineUserCount());
        }

        return status;
    }

    /**
     * TCP通道初始化器
     * 负责配置TCP连接的处理管道
     *
     * @author xxxcrel
     */
    public class TcpChannelInitializer extends ChannelInitializer<SocketChannel> {

        private static final Logger logger = CommonLoggers.POSTOFFICE;

        private SslContext sslContext;

        /**
         * 初始化SSL上下文
         */
        public void initSslContext() throws Exception {
            ServerProperties.TcpConfig tcpConfig = ServerProperties.getTcp();
            ServerProperties.SslConfig sslConfig = tcpConfig.getSsl();

            if (!sslConfig.isEnabled()) {
                logger.info("TCP SSL is disabled");
                return;
            }

            try {
                if (sslConfig.getCertPath().isEmpty() || sslConfig.getKeyPath().isEmpty()) {
                    // 使用自签名证书（仅用于测试）
                    logger.warn("Using self-signed certificate for TCP SSL (NOT for production!)");
                    SelfSignedCertificate ssc = new SelfSignedCertificate();
                    sslContext = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();
                } else {
                    // 使用指定的证书文件
                    logger.info("Using certificate files for TCP SSL: cert={}, key={}",
                            sslConfig.getCertPath(),
                            sslConfig.getKeyPath());
                    sslContext = SslContextBuilder
                            .forServer(new java.io.File(sslConfig.getCertPath()), new java.io.File(sslConfig.getKeyPath()))
                            .build();
                }

                logger.info("TCP SSL context initialized successfully");

            } catch (Exception e) {
                logger.error("Failed to initialize TCP SSL context", e);
                throw e;
            }
        }

        @Override
        protected void initChannel(SocketChannel ch) throws Exception {
            ServerProperties.TcpConfig tcpConfig = ServerProperties.getTcp();
            ChannelPipeline            pipeline  = ch.pipeline();

            // SSL处理器（如果启用）
            if (tcpConfig.getSsl().isEnabled() && sslContext != null) {
                pipeline.addLast("ssl", sslContext.newHandler(ch.alloc()));
                logger.debug("Added SSL handler to TCP pipeline for {}", ch.remoteAddress());
            }

            // 空闲状态处理器，用于检测连接空闲状态
            pipeline.addLast("idle-state",
                    new IdleStateHandler(tcpConfig.getIdleTimeout(),
                            tcpConfig.getIdleTimeout(),
                            tcpConfig.getIdleTimeout(),
                            TimeUnit.SECONDS));

            // TCP消息解码器
            pipeline.addLast("tcp-decoder", new TcpEnvelopeDecoder());

            // TCP消息编码器
            pipeline.addLast("tcp-encoder", new TcpEnvelopeEncoder());

            // TCP服务器处理器
            pipeline.addLast("tcp-handler", tcpServerHandler);

            logger.debug("TCP channel pipeline initialized for {}", ch.remoteAddress());
        }


    }
}
