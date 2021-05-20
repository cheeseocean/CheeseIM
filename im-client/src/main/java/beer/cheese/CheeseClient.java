package beer.cheese;


import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttQoS;

public class CheeseClient {
    MessageHandler messageHandler = new MessageHandler();

    public static void main(String[] args) {
        CheeseClient client = new CheeseClient();
        ExecutorService executor = Executors.newFixedThreadPool(1);
        executor.execute(() -> {
            client.connect("localhost", 51477);
        });
//        client.messageHandler.sendMessage(MqttMessageBuilders.connect().build());
        try {
            TimeUnit.SECONDS.sleep(2);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        client.messageHandler.sendMessage(MqttMessageBuilders.publish()
                .qos(MqttQoS.AT_LEAST_ONCE).payload(Unpooled.copiedBuffer("Hello mqtt", StandardCharsets.UTF_8)).build());
    }

    public void connect(String host, int port) {
        Bootstrap boot = new Bootstrap();
        boot.channel(NioSocketChannel.class)
                .group(new NioEventLoopGroup())
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline()
                                .addLast(messageHandler)
                                .addLast(new ChannelOutboundHandlerAdapter(){
                                    @Override
                                    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                                        System.out.println("out: " + msg);
                                        super.write(ctx, msg, promise);
                                    }
                                })
                                .addLast(new MqttDecoder())
                                .addLast(MqttEncoder.INSTANCE);
                    }
                });
        try {
            ChannelFuture future = boot.connect(new InetSocketAddress(host, port)).sync();
            future.channel().writeAndFlush(MqttMessageBuilders.publish().qos(MqttQoS.AT_LEAST_ONCE).payload(Unpooled.copiedBuffer("Hello", StandardCharsets.UTF_8)).build());
            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

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
        this.ctx = ctx;
        System.out.println("channel Active");
    }

    public void sendMessage(MqttMessage message) {
        System.out.println("start send");
        if (ctx != null){
            System.out.println("send message");
            ctx.writeAndFlush(Unpooled.copiedBuffer("hello", StandardCharsets.UTF_8));
        }
    }
}