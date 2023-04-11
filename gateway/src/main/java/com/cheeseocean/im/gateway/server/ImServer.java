package com.cheeseocean.im.gateway.server;

import com.cheeseocean.im.common.codec.CheeseMessageDecoder;
import com.cheeseocean.im.common.codec.CheeseMessageEncoder;
import com.cheeseocean.im.common.config.IMConfig;
import com.cheeseocean.im.gateway.handler.ConnectionHandler;
import com.cheeseocean.im.gateway.handler.TimeoutHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class ImServer {

    @Autowired
    private IMConfig imConfig;

    private NioEventLoopGroup boss;
    private NioEventLoopGroup worker;

    void start() {
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
                                .addLast(new CheeseMessageDecoder())
                                .addLast(CheeseMessageEncoder.INSTANCE)
                                .addLast(new ConnectionHandler());
                    }
                });
        //@formatter:on
        try {
            ChannelFuture future = boot.bind(imConfig.getGateway().getImPort()).sync();
            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            boss.shutdownGracefully();
            worker.shutdownGracefully();
        }
    }
}
