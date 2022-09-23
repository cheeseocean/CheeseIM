package com.cheeseocean.im;

import com.cheeseocean.im.handler.TimeoutHandler;
import com.cheeseocean.im.handler.WsHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@PropertySource(value = "classpath:gateway.yml", factory = YamlPropertySourceFactory.class)
public class WsServer {

    private static final Logger log = LoggerFactory.getLogger(WsServer.class);

    @Value("${server.port}")
    int port;

    private NioEventLoopGroup boss;
    private NioEventLoopGroup worker;

    public static void main(String[] args) {
        ApplicationContext context = new AnnotationConfigApplicationContext(WsServer.class);
        WsServer server = context.getBean(WsServer.class);
        server.init();
        server.start();
    }

    public void init() {
        boss = new NioEventLoopGroup();
        worker = new NioEventLoopGroup();
    }

    public void start() {
        ServerBootstrap boot = new ServerBootstrap();
        //@formatter:off
        boot.group(boss, worker)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline()
                                .addLast("idleStateHandler", new IdleStateHandler(5, 0, 0, TimeUnit.SECONDS))
                                .addAfter("idleStateHandler", "timeoutHandler", new TimeoutHandler())
                                .addLast(new HttpServerCodec())
                                .addLast(new HttpObjectAggregator(65536))
                                .addLast(new WebSocketServerProtocolHandler("/", null, true))
                                .addLast(new WsHandler());
                    }
                });
        //@formatter:on
        try {
            ChannelFuture future = boot.bind(port).sync();
            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            boss.shutdownGracefully();
            worker.shutdownGracefully();
        }
    }

}
