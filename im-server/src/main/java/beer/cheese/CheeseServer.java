package beer.cheese;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;

import beer.cheese.handler.TimeoutHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.timeout.IdleStateHandler;

@Configuration
@PropertySource(value = "classpath:server.yml", factory = YamlPropertySourceFactory.class)
public class CheeseServer {

    private static final Logger log = LoggerFactory.getLogger(CheeseServer.class);
    @Value("${mqtt.port}")
    int port;
    @Autowired
    Environment env;

    private NioEventLoopGroup boss;
    private NioEventLoopGroup worker;

    public static void main(String[] args) {
        ApplicationContext context = new AnnotationConfigApplicationContext(CheeseServer.class);
        CheeseServer server = context.getBean(CheeseServer.class);
        server.init();
        server.startServer();
    }

    public void init(){
        boss = new NioEventLoopGroup();
        worker = new NioEventLoopGroup();
    }


    public void startServer(){
        ServerBootstrap boot = new ServerBootstrap();
        boot.group(boss, worker)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline()
                                .addLast("idleStateHandler", new IdleStateHandler(5, 0, 0, TimeUnit.SECONDS))
                                .addAfter("idleStateHandler", "timeoutHandler", new TimeoutHandler())
                                .addLast("mqttDecoder", new MqttDecoder())
                                .addLast("mqttEncoder", MqttEncoder.INSTANCE)
                                .addBefore("mqttDecoder", "readMessage", new ChannelInboundHandlerAdapter(){
                                    @Override
                                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                        System.out.println("hello");
                                        log.info("hello");
                                        log.info("msg : {}", msg);
                                       if(msg instanceof MqttMessage){
                                           log.info("is mqtt");
                                       }
                                        ctx.writeAndFlush(msg);
                                    }
                                });
                    }
                });
        try {
            ChannelFuture future = boot.bind(port).sync();
            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }finally {
            boss.shutdownGracefully();
            worker.shutdownGracefully();
        }
    }

}
