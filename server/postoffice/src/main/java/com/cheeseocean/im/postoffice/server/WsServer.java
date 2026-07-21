package com.cheeseocean.im.postoffice.server;

import com.cheeseocean.im.common.core.logging.CommonLoggers;
import com.cheeseocean.im.postoffice.config.ServerProperties;
import com.cheeseocean.im.postoffice.connection.ConnectionManager;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import javax.net.ssl.SSLException;
import java.io.File;
import java.security.cert.CertificateException;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket服务器
 * 基于Netty实现的高性能WebSocket服务器
 *
 * @author xxxcrel
 */
@Component
public class WsServer implements CommandLineRunner, Server {

    private static final Logger logger = CommonLoggers.POSTOFFICE;

    private final ServerProperties serverProperties;
    private final ConnectionManager connectionManager;
    private final WsServerHandler wsServerHandler;
    private ServerProperties.WebSocketConfig websocketConfig;
    private EventLoopGroup                   bossGroup;
    private EventLoopGroup                   workerGroup;
    private ChannelFuture                    channelFuture;
    private long                             startTime;

    public WsServer(ServerProperties serverProperties,
                    ConnectionManager connectionManager,
                    WsServerHandler wsServerHandler) {
        this.serverProperties = serverProperties;
        this.connectionManager = connectionManager;
        this.wsServerHandler = wsServerHandler;
    }

    @Override
    public void run(String... args) throws Exception {
        websocketConfig = serverProperties.getWebsocket();
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
        WsChannelInitializer channelInitializer = new WsChannelInitializer();
        channelInitializer.initSslContext();

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
                    .childOption(ChannelOption.SO_SNDBUF, websocketConfig.getSendBufferSize())
                    .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(
                            websocketConfig.getWriteBufferLowWaterMark(),
                            websocketConfig.getWriteBufferHighWaterMark()));

            startTime = System.currentTimeMillis();
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
                    serverProperties.getConnection().getMaxConnections());

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
        status.setProtocol("WebSocket");
        ServerProperties.WebSocketConfig currentConfig =
                websocketConfig != null ? websocketConfig : serverProperties.getWebsocket();
        status.setPort(currentConfig.getPort());
        status.setSslEnabled(currentConfig.getSsl().isEnabled());
        status.setWebsocketPath(currentConfig.getPath());
        status.setBossThreads(currentConfig.getBossThreads());
        status.setWorkerThreads(currentConfig.getActualWorkerThreads());
        status.setStartTime(startTime);

        if (connectionManager != null) {
            status.setTotalConnections(connectionManager.getTotalConnectionCount());
            status.setOnlineUsers(connectionManager.getOnlineUserCount());
        }

        return status;
    }

    class WsChannelInitializer extends ChannelInitializer<SocketChannel> {

        private SslContext sslContext;

        /**
         * 初始化SSL上下文
         */
        public void initSslContext() throws CertificateException, SSLException {
            ServerProperties.WebSocketConfig websocketConfig = serverProperties.getWebsocket();
            ServerProperties.SslConfig       sslConfig       = websocketConfig.getSsl();

            if (sslConfig.isEnabled()) {
                if (!sslConfig.getCertPath().isEmpty() && !sslConfig.getKeyPath().isEmpty()) {
                    // 使用指定的证书和私钥
                    File certFile = new File(sslConfig.getCertPath());
                    File keyFile  = new File(sslConfig.getKeyPath());
                    sslContext = SslContextBuilder.forServer(certFile, keyFile).build();
                    logger.info("SSL enabled with custom certificate: cert={}, key={}",
                            sslConfig.getCertPath(), sslConfig.getKeyPath());
                } else {
                    // 使用自签名证书（仅用于开发环境）
                    SelfSignedCertificate ssc = new SelfSignedCertificate();
                    sslContext = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();
                    logger.warn("SSL enabled with self-signed certificate (development only)");
                }
            }
        }

        @Override
        protected void initChannel(SocketChannel ch) throws Exception {
            ServerProperties.WebSocketConfig websocketConfig = serverProperties.getWebsocket();
            ChannelPipeline                  pipeline        = ch.pipeline();

            // SSL处理器（如果启用SSL）
            if (sslContext != null) {
                pipeline.addLast("ssl", sslContext.newHandler(ch.alloc()));
            }

            // HTTP编解码器
            pipeline.addLast("http-codec", new HttpServerCodec());

            // HTTP对象聚合器，将多个HTTP消息聚合成一个完整的HTTP消息
            pipeline.addLast("http-aggregator", new HttpObjectAggregator(websocketConfig.getMaxFrameLength()));

            // 支持大文件传输
            pipeline.addLast("http-chunked", new ChunkedWriteHandler());

            // WebSocket压缩处理器
            pipeline.addLast("websocket-compression", new WebSocketServerCompressionHandler());

            // WebSocket协议处理器
            pipeline.addLast("websocket-protocol",
                    new WebSocketServerProtocolHandler(websocketConfig.getPath(),
                            null,
                            true,
                            websocketConfig.getMaxFrameLength()));

            // 空闲状态处理器，用于检测连接空闲状态
            pipeline.addLast("idle-state",
                    new IdleStateHandler(websocketConfig.getIdleTimeout(),
                            websocketConfig.getIdleTimeout(),
                            websocketConfig.getIdleTimeout(),
                            TimeUnit.SECONDS));

            // 自定义WebSocket处理器
            pipeline.addLast("websocket-handler", wsServerHandler);

            logger.debug("Channel initialized: {}", ch.remoteAddress());
        }
    }
}
