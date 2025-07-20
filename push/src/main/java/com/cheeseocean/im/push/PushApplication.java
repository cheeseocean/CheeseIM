package com.cheeseocean.im.push;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.kafka.annotation.EnableKafka;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * CheeseIM Push Service 启动类
 * 推送服务主应用程序入口
 * 
 * @author CheeseIM
 */
@SpringBootApplication
@EnableKafka
@EnableDubbo
public class PushApplication {
    
    private static final Logger logger = LoggerFactory.getLogger(PushApplication.class);
    
    public static void main(String[] args) {
        try {
            ConfigurableApplicationContext context = SpringApplication.run(PushApplication.class, args);
            Environment env = context.getEnvironment();
            
            String protocol = "http";
            if (env.getProperty("server.ssl.key-store") != null) {
                protocol = "https";
            }
            
            String serverPort = env.getProperty("server.port", "8083");
            String contextPath = env.getProperty("server.servlet.context-path", "");
            String hostAddress = "localhost";
            
            try {
                hostAddress = InetAddress.getLocalHost().getHostAddress();
            } catch (UnknownHostException e) {
                logger.warn("无法获取本机IP地址，使用localhost");
            }
            
            logger.info("\n----------------------------------------------------------\n" +
                       "🚀 CheeseIM Push Service 启动成功!\n" +
                       "📋 应用信息:\n" +
                       "   - 应用名称: {}\n" +
                       "   - 运行环境: {}\n" +
                       "   - 服务地址: {}://{}:{}{}\n" +
                       "   - 配置文件: {}\n" +
                       "\n" +
                       "📡 API接口:\n" +
                       "   - 健康检查: {}://{}:{}{}/api/v1/push/health\n" +
                       "   - 服务状态: {}://{}:{}{}/api/v1/push/status\n" +
                       "   - 推送统计: {}://{}:{}{}/api/v1/push/stats/push\n" +
                       "   - API文档: {}://{}:{}{}/swagger-ui.html\n" +
                       "\n" +
                       "📊 监控端点:\n" +
                       "   - 健康检查: {}://{}:{}{}/actuator/health\n" +
                       "   - 应用信息: {}://{}:{}{}/actuator/info\n" +
                       "   - 指标监控: {}://{}:{}{}/actuator/metrics\n" +
                       "   - Prometheus: {}://{}:{}{}/actuator/prometheus\n" +
                       "\n" +
                       "🔗 集成服务:\n" +
                       "   - Redis: {}\n" +
                       "   - Kafka: {}\n" +
                       "   - Nacos: {}\n" +
                       "\n" +
                       "📱 推送提供商:\n" +
                       "   - APNs: {}\n" +
                       "   - FCM: {}\n" +
                       "   - JPush: {}\n" +
                       "   - Huawei: {}\n" +
                       "\n" +
                       "📋 Kafka Topics:\n" +
                       "   - 消费: cheese_im_to_push (推送消息请求)\n" +
                       "   - 消费/生产: cheese_im_offline_push (离线推送消息)\n" +
                       "\n" +
                       "🔗 Dubbo Services:\n" +
                       "   - 消费: PostofficeOnlinePushService (在线推送)\n" +
                       "\n" +
                       "💡 快速测试:\n" +
                       "   curl {}://{}:{}{}/api/v1/push/health\n" +
                       "   curl {}://{}:{}{}/api/v1/push/status\n" +
                       "----------------------------------------------------------",
                       
                       env.getProperty("spring.application.name", "push"),
                       env.getActiveProfiles().length > 0 ? String.join(",", env.getActiveProfiles()) : "default",
                       protocol, hostAddress, serverPort, contextPath,
                       env.getActiveProfiles().length > 0 ? 
                           "application-" + String.join(",", env.getActiveProfiles()) + ".yml" : "application.yml",
                       
                       protocol, hostAddress, serverPort, contextPath,
                       protocol, hostAddress, serverPort, contextPath,
                       protocol, hostAddress, serverPort, contextPath,
                       protocol, hostAddress, serverPort, contextPath,
                       
                       protocol, hostAddress, serverPort, contextPath,
                       protocol, hostAddress, serverPort, contextPath,
                       protocol, hostAddress, serverPort, contextPath,
                       protocol, hostAddress, serverPort, contextPath,
                       
                       env.getProperty("spring.data.redis.host", "localhost") + ":" + 
                           env.getProperty("spring.data.redis.port", "6379"),
                       env.getProperty("spring.kafka.bootstrap-servers", "localhost:9092"),
                       env.getProperty("dubbo.registry.address", "nacos://localhost:8848"),
                       
                       env.getProperty("cheese.im.push.apns.enabled", "false"),
                       env.getProperty("cheese.im.push.fcm.enabled", "false"),
                       env.getProperty("cheese.im.push.jpush.enabled", "false"),
                       env.getProperty("cheese.im.push.huawei.enabled", "false"),
                       
                       protocol, hostAddress, serverPort, contextPath,
                       protocol, hostAddress, serverPort, contextPath
            );
            
        } catch (Exception e) {
            logger.error("CheeseIM Push Service 启动失败", e);
            System.exit(1);
        }
    }
}
