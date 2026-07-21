package com.cheeseocean.im.postoffice.config;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * IM server transport configuration.
 *
 * @author xxxcrel
 */
@Configuration
@ConfigurationProperties(prefix = "cheeseim.postoffice")
@Data
public class ServerProperties {

    private final TcpConfig        tcp        = new TcpConfig();
    private final WebSocketConfig  websocket  = new WebSocketConfig();
    private final ConnectionConfig connection = new ConnectionConfig();
    private final BusinessConfig   business   = new BusinessConfig();
    private final DeliveryConfig delivery = new DeliveryConfig();
    private final SessionValidationConfig sessionValidation = new SessionValidationConfig();
    private final RouteHeartbeatConfig routeHeartbeat = new RouteHeartbeatConfig();
    private final LoginLeaseConfig loginLease = new LoginLeaseConfig();
    private final MessageLimitsConfig messageLimits = new MessageLimitsConfig();

    /** 客户端 CHAT_SEND 的 envelope 与各类 content 上限，单位均为字节。 */
    @Data
    public static class MessageLimitsConfig {
        private int maxEnvelopeBodyBytes = 65536;
        private int maxTextBytes = 16384;
        private int maxCustomBytes = 65536;
        private int maxMediaMetadataBytes = 32768;
        private int maxDefaultBytes = 32768;
    }

    /** 长连接命令处理线程池配置，独立于 Netty EventLoop。 */
    @Data
    public static class BusinessConfig {
        private int threads = 0;
        private int queueCapacity = 20000;

        public int getActualThreads() {
            return threads <= 0
                    ? Math.max(2, Runtime.getRuntime().availableProcessors() * 2)
                    : threads;
        }
    }

    /** 在线投递等待 ChannelFuture 与延迟终态收口的有界资源配置。 */
    @Data
    public static class DeliveryConfig {
        private long writeTimeoutMs = 1_000L;
        private int completionThreads = 2;
        private int completionQueueCapacity = 10_000;
    }

    /**
     * 已认证长连接的服务端 session 周期复核。
     *
     * <p>主动撤销仍通过 kickoff 立即关闭；本地租约用于吸收高频心跳并兜底丢失的撤销通知。</p>
     */
    @Data
    public static class SessionValidationConfig {
        private long intervalMs = 60_000L;
    }

    /** 在线路由心跳合并与批刷参数。 */
    @Data
    public static class RouteHeartbeatConfig {
        private long persistIntervalMs = 60_000L;
        private long flushIntervalMs = 1_000L;
        private int flushBatchSize = 20_000;
    }

    /**
     * 跨节点登录 lease。滚动升级必须先全量部署消费者，再显式开启 enforce。
     */
    @Data
    public static class LoginLeaseConfig {
        private boolean enforce;
        private long ttlMs = 180_000L;
        private long keyGraceMs = 60_000L;
        private long renewIntervalMs = 60_000L;
        private int renewBatchSize = 20_000;
    }

    @Data
    public static class ConnectionConfig {
        private String multiLoginStrategy = "SAME_TERMINAL_KICK";
        private long   timeoutMs = 300_000L;
        /** 单 postoffice 节点的 TCP + WS 总连接上限，包含未认证连接。 */
        private int    maxConnections = 100_000;
        /** 单节点内单用户已认证连接上限；跨节点一致性由后续全局 login policy 负责。 */
        private int    maxConnectionsPerUser = 10;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class TcpConfig extends BaseTransportConfig {
        private boolean enabled = true;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class WebSocketConfig extends BaseTransportConfig {
        private boolean enabled = true;
        private String  path    = "/ws";

    }

    @Data
    public abstract static class BaseTransportConfig {
        private final SslConfig ssl               = new SslConfig();
        private       int       port;
        private       int       bossThreads       = 1;
        private       int       workerThreads     = 0;
        private       int       idleTimeout       = 300;
        private       int       heartbeatInterval = 30;
        private       int       receiveBufferSize = 65536;
        private       int       sendBufferSize    = 65536;
        private       boolean   tcpNoDelay        = true;
        private       boolean   keepAlive         = true;
        private       int       backlog           = 1024;
        private       int       maxFrameLength    = 65536;
        /** Netty 待发送字节低水位，恢复 writable。 */
        private       int       writeBufferLowWaterMark = 32 * 1024;
        /** Netty 待发送字节高水位，超过后变为 unwritable。 */
        private       int       writeBufferHighWaterMark = 64 * 1024;

        public int getActualWorkerThreads() {
            return workerThreads <= 0
                    ? Runtime.getRuntime().availableProcessors() * 2
                    : workerThreads;
        }
    }

    @Data
    public static class SslConfig {
        private boolean enabled;
        private String  certPath = "";
        private String  keyPath  = "";
    }
}
