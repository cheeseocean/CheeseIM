package com.cheeseocean.im.postoffice.server;

import com.cheeseocean.im.postoffice.codec.TcpMessageDecoder;
import com.cheeseocean.im.postoffice.codec.TcpMessageEncoder;
import com.cheeseocean.im.postoffice.config.NettyConfig;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * TCP通道初始化器
 * 负责配置TCP连接的处理管道
 * 
 * @author CheeseIM
 */
@Component
public class TcpChannelInitializer extends ChannelInitializer<SocketChannel> {
    
    private static final Logger logger = LoggerFactory.getLogger(TcpChannelInitializer.class);
    
    @Autowired
    private NettyConfig nettyConfig;
    
    @Autowired
    private TcpServerHandler tcpServerHandler;
    
    @Value("${postoffice.tcp.ssl.enabled:false}")
    private boolean sslEnabled;
    
    @Value("${postoffice.tcp.ssl.cert-path:}")
    private String certPath;
    
    @Value("${postoffice.tcp.ssl.key-path:}")
    private String keyPath;
    
    private SslContext sslContext;
    
    /**
     * 初始化SSL上下文
     */
    public void initSslContext() throws Exception {
        if (!sslEnabled) {
            logger.info("TCP SSL is disabled");
            return;
        }
        
        try {
            if (certPath.isEmpty() || keyPath.isEmpty()) {
                // 使用自签名证书（仅用于测试）
                logger.warn("Using self-signed certificate for TCP SSL (NOT for production!)");
                SelfSignedCertificate ssc = new SelfSignedCertificate();
                sslContext = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();
            } else {
                // 使用指定的证书文件
                logger.info("Using certificate files for TCP SSL: cert={}, key={}", certPath, keyPath);
                sslContext = SslContextBuilder.forServer(new java.io.File(certPath), new java.io.File(keyPath)).build();
            }
            
            logger.info("TCP SSL context initialized successfully");
            
        } catch (Exception e) {
            logger.error("Failed to initialize TCP SSL context", e);
            throw e;
        }
    }
    
    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        
        // SSL处理器（如果启用）
        if (sslEnabled && sslContext != null) {
            pipeline.addLast("ssl", sslContext.newHandler(ch.alloc()));
            logger.debug("Added SSL handler to TCP pipeline for {}", ch.remoteAddress());
        }
        
        // 空闲状态处理器，用于检测连接空闲状态
        pipeline.addLast("idle-state", 
                        new IdleStateHandler(nettyConfig.getIdleTimeout(), 
                                           nettyConfig.getIdleTimeout(), 
                                           nettyConfig.getIdleTimeout(), 
                                           TimeUnit.SECONDS));
        
        // TCP消息解码器
        pipeline.addLast("tcp-decoder", new TcpMessageDecoder());
        
        // TCP消息编码器
        pipeline.addLast("tcp-encoder", new TcpMessageEncoder());
        
        // TCP服务器处理器
        pipeline.addLast("tcp-handler", tcpServerHandler);
        
        logger.debug("TCP channel pipeline initialized for {}", ch.remoteAddress());
    }
    

}
