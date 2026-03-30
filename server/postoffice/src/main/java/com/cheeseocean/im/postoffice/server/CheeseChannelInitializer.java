package com.cheeseocean.im.postoffice.server;

import com.cheeseocean.im.postoffice.codec.TcpEnvelopeDecoder;
import com.cheeseocean.im.postoffice.codec.TcpEnvelopeEncoder;
import com.cheeseocean.im.postoffice.config.IMServerConfig;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.handler.timeout.IdleStateHandler;
import com.cheeseocean.im.common.core.logging.CommonLoggers;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * TCP通道初始化器
 * 负责配置TCP连接的处理管道
 * 
 * @author xxxcrel
 */
@Component
public class CheeseChannelInitializer extends ChannelInitializer<SocketChannel> {
    
    private static final Logger logger = CommonLoggers.POSTOFFICE;
    
    @Autowired
    private IMServerConfig IMServerConfig;
    
    @Autowired
    private CheeseServerHandler cheeseServerHandler;

    private SslContext sslContext;
    
    /**
     * 初始化SSL上下文
     */
    public void initSslContext() throws Exception {
        IMServerConfig.TcpConfig tcpConfig = IMServerConfig.getTcp();
        IMServerConfig.SslConfig sslConfig = tcpConfig.getSsl();

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
        IMServerConfig.TcpConfig tcpConfig = IMServerConfig.getTcp();
        ChannelPipeline pipeline = ch.pipeline();
        
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
        pipeline.addLast("tcp-handler", cheeseServerHandler);
        
        logger.debug("TCP channel pipeline initialized for {}", ch.remoteAddress());
    }
    

}
