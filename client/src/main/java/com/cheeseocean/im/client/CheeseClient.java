package com.cheeseocean.im.client;


import com.cheeseocean.im.common.codec.CheeseMessageDecoder;
import com.cheeseocean.im.common.codec.Hessian2ObjectInput;
import com.cheeseocean.im.common.codec.Hessian2ObjectOutput;
import com.cheeseocean.im.common.constant.IMConstant;
import com.cheeseocean.im.common.entity.CheeseMessage;
import com.cheeseocean.im.common.entity.CheeseRequest;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.mqtt.MqttMessage;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
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
                                .addLast(RequestEncoder.INSTANCE)
                                .addLast(new CheeseMessageDecoder());

                    }
                });
        ChannelFuture future = boot.connect(new InetSocketAddress(host, port));
        future.addListener((ChannelFutureListener) f -> this.channel = f.channel())
                .syncUninterruptibly();
        log.info("start client successful");
    }

    public void sendMessage(String message) {

        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        Hessian2ObjectOutput out = new Hessian2ObjectOutput(ba);
        try {
            out.writeObject(CheeseMessage.builder().rid("1234").content(message.getBytes()).build());
            out.flushBuffer();
//            Hessian2ObjectInput in = new Hessian2ObjectInput(new ByteArrayInputStream(ba.toByteArray()));
//            CheeseMessage msg = in.readObject(CheeseMessage.class);
//            log.info("msg.content:{}", msg.getContent());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        CheeseRequest cheeseRequest = CheeseRequest.builder()
                .msgIncr(1)
                .uid("1231")
                .type(IMConstant.WSSendMsg)
                .payload(ba.toByteArray())
                .build();
        log.info("send msg to channel:{}", channel.id());
        channel.writeAndFlush(cheeseRequest);
    }
}

@Slf4j
class RequestEncoder extends MessageToByteEncoder<CheeseRequest> {

    public static RequestEncoder INSTANCE = new RequestEncoder();
    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, CheeseRequest cheeseRequest, ByteBuf byteBuf) throws Exception {
        log.info("encode request:{}", cheeseRequest);
        Hessian2ObjectOutput output = new Hessian2ObjectOutput(new ByteBufOutputStream(byteBuf));
        output.writeObject(cheeseRequest);
        output.flushBuffer();
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