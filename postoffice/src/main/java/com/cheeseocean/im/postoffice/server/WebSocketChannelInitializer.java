package com.cheeseocean.im.postoffice.server;

import com.cheeseocean.im.postoffice.config.NettyConfig;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
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
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLException;
import java.io.File;
import java.security.cert.CertificateException;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket Channel初始化器
 * 配置Netty Channel的处理器链
 * 
 * @author CheeseIM
 */
@Component
public class WebSocketChannelInitializer extends ChannelInitializer<SocketChannel> {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketChannelInitializer.class);
    
    @Autowired
    private NettyConfig nettyConfig;
    
    @Autowired
    private WebSocketServerHandler webSocketServerHandler;
    
    private SslContext sslContext;
    
    /**
     * 初始化SSL上下文
     */
    public void initSslContext() throws CertificateException, SSLException {
        if (nettyConfig.isSslEnabled()) {
            if (nettyConfig.getSslCertPath() != null && nettyConfig.getSslKeyPath() != null) {
                // 使用指定的证书和私钥
                File certFile = new File(nettyConfig.getSslCertPath());
                File keyFile = new File(nettyConfig.getSslKeyPath());
                sslContext = SslContextBuilder.forServer(certFile, keyFile).build();
                logger.info("SSL enabled with custom certificate: cert={}, key={}", 
                           nettyConfig.getSslCertPath(), nettyConfig.getSslKeyPath());
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
        ChannelPipeline pipeline = ch.pipeline();
        
        // SSL处理器（如果启用SSL）
        if (sslContext != null) {
            pipeline.addLast("ssl", sslContext.newHandler(ch.alloc()));
        }
        
        // HTTP编解码器
        pipeline.addLast("http-codec", new HttpServerCodec());
        
        // HTTP对象聚合器，将多个HTTP消息聚合成一个完整的HTTP消息
        pipeline.addLast("http-aggregator", new HttpObjectAggregator(nettyConfig.getMaxFrameLength()));
        
        // 支持大文件传输
        pipeline.addLast("http-chunked", new ChunkedWriteHandler());
        
        // WebSocket压缩处理器
        pipeline.addLast("websocket-compression", new WebSocketServerCompressionHandler());
        
        // WebSocket协议处理器
        pipeline.addLast("websocket-protocol", 
                        new WebSocketServerProtocolHandler(nettyConfig.getWebsocketPath(), 
                                                          null, 
                                                          true, 
                                                          nettyConfig.getMaxFrameLength()));
        
        // 空闲状态处理器，用于检测连接空闲状态
        pipeline.addLast("idle-state", 
                        new IdleStateHandler(nettyConfig.getIdleTimeout(), 
                                           nettyConfig.getIdleTimeout(), 
                                           nettyConfig.getIdleTimeout(), 
                                           TimeUnit.SECONDS));
        
        // 自定义WebSocket处理器
        pipeline.addLast("websocket-handler", webSocketServerHandler);
        
        logger.debug("Channel initialized: {}", ch.remoteAddress());
    }
}
