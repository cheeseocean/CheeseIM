package com.cheeseocean.im.postoffice.connection;

import com.cheeseocean.im.common.api.enums.ConnectionState;
import com.cheeseocean.im.common.api.enums.PlatformType;
import io.netty.channel.Channel;
import lombok.Data;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 用户连接实体
 * 封装用户的WebSocket连接信息和状态
 *
 * @author xxxcrel
 */
@Data
public class UserConnection implements Serializable {

    // 连接状态常量
    public static final  int               STATUS_CONNECTING    = 0;
    public static final  int               STATUS_CONNECTED     = 1;
    public static final  int               STATUS_AUTHENTICATED = 2;
    public static final  int               STATUS_DISCONNECTED  = 3;
    private static final long              serialVersionUID     = 1L;
    /**
     * 心跳计数器
     */
    private final        AtomicLong        heartbeatCount       = new AtomicLong(0);
    /**
     * 发送消息计数器
     */
    private final        AtomicLong        sendMsgCount         = new AtomicLong(0);
    /**
     * 接收消息计数器
     */
    private final        AtomicLong        recvMsgCount         = new AtomicLong(0);
    /**
     * 连接ID，全局唯一
     */
    private              String            connectionID;
    /**
     * 用户ID
     */
    private              String            userID;
    /**
     * 平台ID (1:iOS 2:Android 3:Windows 4:OSX 5:WEB 6:MiniWeb 7:Linux)
     */
    private              PlatformType      platformType;
    /**
     * 会话ID
     */
    private              String            sessionId;
    /**
     * 设备ID
     */
    private              String            deviceId;
    /**
     * 租户ID
     */
    private              String            tenantId;
    /**
     * 平台标识
     */
    private              String            platform;
    /**
     * token版本
     */
    private              Long              tokenVersion;
    /** Redis 全局登录 lease 的 fencing generation；未启用 lease 时为 null。 */
    private              Long              loginLeaseGeneration;
    /**
     * 协议类型 (WebSocket/TCP)
     */
    private              String            protocol;
    /**
     * Netty Channel
     */
    private transient    Channel           channel;
    /**
     * JWT Token
     */
    private              String            token;
    /**
     * 连接建立时间
     */
    private              Long              connectTime;
    /**
     * 最后活跃时间
     */
    private volatile     Long              lastActiveTime;
    /**
     * 是否已认证
     */
    private volatile     boolean           authenticated;
    /**
     * 客户端IP地址
     */
    private              String            clientIP;
    /**
     * 用户代理信息
     */
    private              String            userAgent;
    /**
     * 连接状态 (0:连接中 1:已连接 2:已认证 3:已断开)
     */
    private volatile     int               status;
    /**
     * 连接身份上下文
     */
    private              ConnectionContext context;

    public UserConnection() {
        this.connectTime = System.currentTimeMillis();
        this.lastActiveTime = System.currentTimeMillis();
        this.authenticated = false;
        this.status = STATUS_CONNECTING;
    }

    public UserConnection(String connectionID, String userID, Integer platformType, Channel channel) {
        this();
        this.connectionID = connectionID;
        this.userID = userID;
        this.platformType = PlatformType.fromCode(platformType);
        this.channel = channel;
        this.status = STATUS_CONNECTED;
    }

    /**
     * 更新最后活跃时间
     */
    public void updateLastActiveTime() {
        this.lastActiveTime = System.currentTimeMillis();
        if (context != null) {
            context.setLastHeartbeatAt(this.lastActiveTime);
        }
    }

    /**
     * 设置为已认证状态
     */
    public void setAuthenticated(String token) {
        this.authenticated = true;
        this.token = token;
        this.status = STATUS_AUTHENTICATED;
        if (context != null) {
            context.setState(ConnectionState.AUTHENTICATED);
        }
        updateLastActiveTime();
    }

    /**
     * 检查连接是否活跃
     */
    public boolean isActive() {
        return channel != null && channel.isActive();
    }

    /**
     * 检查连接是否超时
     */
    public boolean isTimeout(long timeoutMs) {
        return System.currentTimeMillis() - lastActiveTime > timeoutMs;
    }

    /**
     * 增加心跳计数
     */
    public void incrementHeartbeat() {
        heartbeatCount.incrementAndGet();
        updateLastActiveTime();
    }

    /**
     * 增加发送消息计数
     */
    public void incrementSendMsg() {
        sendMsgCount.incrementAndGet();
        updateLastActiveTime();
    }

    /**
     * 增加接收消息计数
     */
    public void incrementRecvMsg() {
        recvMsgCount.incrementAndGet();
        updateLastActiveTime();
    }

    /**
     * 获取连接持续时间（毫秒）
     */
    public long getConnectionDuration() {
        return System.currentTimeMillis() - connectTime;
    }

    /**
     * 获取平台名称
     */
    public String getPlatformName() {
        return platformType == null ? PlatformType.UNKNOWN.getDisplayName() : platformType.getDisplayName();
    }

    /**
     * 获取状态描述
     */
    public String getStatusDesc() {
        switch (status) {
            case STATUS_CONNECTING:
                return "连接中";
            case STATUS_CONNECTED:
                return "已连接";
            case STATUS_AUTHENTICATED:
                return "已认证";
            case STATUS_DISCONNECTED:
                return "已断开";
            default:
                return "未知状态";
        }
    }
}
