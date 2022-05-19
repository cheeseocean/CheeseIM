package beer.cheese;


import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
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
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;

public class CheeseClient {
    MessageHandler messageHandler = new MessageHandler();

    Channel channel;

    public static void main(String[] args) throws InterruptedException {
        CheeseClient client = new CheeseClient();
        ExecutorService executor = Executors.newFixedThreadPool(1);
        client.connect("localhost", 51477);
//        client.messageHandler.sendMessage(MqttMessageBuilders.connect().build());
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
//                                .addLast(messageHandler)
                                .addLast(new MqttDecoder())
                                .addLast(MqttEncoder.INSTANCE)
                                .addLast(new ChannelOutboundHandlerAdapter() {
                                    @Override
                                    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                                        System.out.println("out: " + msg);
                                        System.out.println(msg instanceof MqttMessage);
                                        ByteBuf payload = (ByteBuf) ((MqttMessage) msg).payload();
                                        System.out.println(convertByteBufToString(payload));
                                        super.write(ctx, msg, promise);
                                    }
                                }).addLast(new ChannelOutboundHandlerAdapter() {
                            @Override
                            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                                System.out.println("second");
                                super.write(ctx, msg, promise);
                            }
                        });
                    }
                });
        ChannelFuture future = boot.connect(new InetSocketAddress(host, port)).sync();
        MqttPublishMessage publishMsg = MqttMessageBuilders.publish()
                .qos(MqttQoS.AT_LEAST_ONCE)
                .topicName("AddFriend")
                .messageId(10)
                .payload(Unpooled.copiedBuffer("hello world", StandardCharsets.UTF_8))
                .build();
        future.channel().writeAndFlush(publishMsg);

        future.addListener((ChannelFutureListener) f -> this.channel = f.channel());

    }

    public String convertByteBufToString(ByteBuf buf) {
        String str;
        if (buf.hasArray()) { // 处理堆缓冲区
            str = new String(buf.array(), buf.arrayOffset() + buf.readerIndex(), buf.readableBytes());
        } else { // 处理直接缓冲区以及复合缓冲区
            byte[] bytes = new byte[buf.readableBytes()];
            buf.getBytes(buf.readerIndex(), bytes);
            str = new String(bytes, 0, buf.readableBytes());
        }
        return str;
    }

    public void sendMessage(String message) {
        MqttPublishMessage publishMsg = MqttMessageBuilders.publish()
                .qos(MqttQoS.AT_LEAST_ONCE)
                .topicName("AddFriend")
                .messageId(10)
                .payload(Unpooled.copiedBuffer(message, StandardCharsets.UTF_8))
                .build();
        channel.writeAndFlush(publishMsg);
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

    public void sendMessage(MqttMessage message) {
        System.out.println("start send");
        if (ctx != null) {
            System.out.println("send message");
        }
    }
}