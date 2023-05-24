package com.cheeseocean.im.client;


import com.cheeseocean.im.common.codec.CheeseMessageDecoder;
import com.cheeseocean.im.common.codec.CheeseMessageEncoder;
import com.cheeseocean.im.common.constant.IMConstant;
import com.cheeseocean.im.common.entity.UserRequest;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.mqtt.MqttMessage;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class CheeseClient {
    MessageHandler messageHandler = new MessageHandler();

    Channel channel;

    public static void main(String[] args) throws InterruptedException {
        CheeseClient client = new CheeseClient();
        ExecutorService executor = Executors.newFixedThreadPool(1);
        client.connect("localhost", 51477);
        try {
            TimeUnit.SECONDS.sleep(2);
            client.sendMessage("hello");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void connect(String host, int port) throws InterruptedException {
        Bootstrap boot = new Bootstrap();
        boot.channel(NioSocketChannel.class)
                .group(new NioEventLoopGroup())
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline()
                                .addLast(new CheeseMessageDecoder())
                                .addLast(CheeseMessageEncoder.INSTANCE);

                    }
                });
        ChannelFuture future = boot.connect(new InetSocketAddress(host, port)).sync();
        future.addListener((ChannelFutureListener) f -> this.channel = f.channel());
    }

    public void sendMessage(String message) {
        UserRequest userRequest = UserRequest.builder()
                .msgIncr(1)
                .uid("1231")
                .type(IMConstant.WSSendMsg)
                .payload(message.getBytes())
                .build();
        channel.writeAndFlush(userRequest);
    }
}

class MessageHandler extends ChannelDuplexHandler {
    ChannelHandlerContext ctx;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof MqttMessage) {
            System.out.println("is mqtt message");
            System.out.println(((MqttMessage) msg).payload());
        }
        System.out.println("Client recive: " + msg);
        System.out.println("channel read");
        super.channelRead(ctx, msg);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        this.ctx = ctx;
        System.out.println("channel Active");
    }

}