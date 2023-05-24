package com.cheeseocean.im.postoffice.server;

import com.cheeseocean.im.postoffice.config.PostofficeProperties;
import com.cheeseocean.im.postoffice.connection.ConnectionHolder;
import com.cheeseocean.im.postoffice.handler.TimeoutHandler;
import com.cheeseocean.im.postoffice.handler.WsHandler;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class WsServer {

    @Autowired
    private PostofficeProperties postofficeProperties;

    @Autowired
    private ConnectionHolder connectionHolder;

    private NioEventLoopGroup boss;
    private NioEventLoopGroup worker;


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
                                .addLast(new WsHandler(connectionHolder))
                                .addLast();
                    }
                });
        //@formatter:on
        try {
            ChannelFuture future = boot.bind(postofficeProperties.getWsPort()).sync();
            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            boss.shutdownGracefully();
            worker.shutdownGracefully();
        }

    }

}
