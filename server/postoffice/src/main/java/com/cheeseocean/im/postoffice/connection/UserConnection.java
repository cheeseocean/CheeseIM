package com.cheeseocean.im.postoffice.connection;

import io.netty.channel.Channel;
import java.io.Serializable;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 用户连接实体
 * 封装用户的WebSocket连接信息和状态
 * 
 * @author CheeseIM
 */
public class UserConnection implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 连接ID，全局唯一
     */
    private String connectionID;
    
    /**
     * 用户ID
     */
    private String userID;
    
    /**
     * 平台ID (1:iOS 2:Android 3:Windows 4:OSX 5:WEB 6:MiniWeb 7:Linux)
     */
    private Integer platformID;

    /**
     * 会话ID
     */
    private String sessionID;

    /**
     * 设备ID
     */
    private String deviceID;

    /**
     * 租户ID
     */
    private String tenantID;

    /**
     * 平台标识
     */
    private String platform;

    /**
     * token版本
     */
    private Long tokenVersion;

    /**
     * 协议类型 (WebSocket/TCP)
     */
    private String protocol;

    /**
     * Netty Channel
     */
    private transient Channel channel;
    
    /**
     * JWT Token
     */
    private String token;
    
    /**
     * 连接建立时间
     */
    private Long connectTime;
    
    /**
     * 最后活跃时间
     */
    private volatile Long lastActiveTime;
    
    /**
     * 是否已认证
     */
    private volatile boolean authenticated;
    
    /**
     * 客户端IP地址
     */
    private String clientIP;
    
    /**
     * 用户代理信息
     */
    private String userAgent;
    
    /**
     * 连接状态 (0:连接中 1:已连接 2:已认证 3:已断开)
     */
    private volatile int status;

    /**
     * 连接身份上下文
     */
    private ConnectionContext context;
    
    /**
     * 心跳计数器
     */
    private final AtomicLong heartbeatCount = new AtomicLong(0);
    
    /**
     * 发送消息计数器
     */
    private final AtomicLong sendMsgCount = new AtomicLong(0);
    
    /**
     * 接收消息计数器
     */
    private final AtomicLong recvMsgCount = new AtomicLong(0);
    
    // 连接状态常量
    public static final int STATUS_CONNECTING = 0;
    public static final int STATUS_CONNECTED = 1;
    public static final int STATUS_AUTHENTICATED = 2;
    public static final int STATUS_DISCONNECTED = 3;
    
    public UserConnection() {
        this.connectTime = System.currentTimeMillis();
        this.lastActiveTime = System.currentTimeMillis();
        this.authenticated = false;
        this.status = STATUS_CONNECTING;
    }
    
    public UserConnection(String connectionID, String userID, Integer platformID, Channel channel) {
        this();
        this.connectionID = connectionID;
        this.userID = userID;
        this.platformID = platformID;
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
            context.setState(com.cheeseocean.im.common.enums.ConnectionState.AUTHENTICATED);
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
        if (platformID == null) {
            return "Unknown";
        }
        switch (platformID) {
            case 1: return "iOS";
            case 2: return "Android";
            case 3: return "Windows";
            case 4: return "OSX";
            case 5: return "WEB";
            case 6: return "MiniWeb";
            case 7: return "Linux";
            default: return "Unknown";
        }
    }
    
    /**
     * 获取状态描述
     */
    public String getStatusDesc() {
        switch (status) {
            case STATUS_CONNECTING: return "连接中";
            case STATUS_CONNECTED: return "已连接";
            case STATUS_AUTHENTICATED: return "已认证";
            case STATUS_DISCONNECTED: return "已断开";
            default: return "未知状态";
        }
    }
    
    // ============ Getter and Setter ============
    
    public String getConnectionID() {
        return connectionID;
    }
    
    public void setConnectionID(String connectionID) {
        this.connectionID = connectionID;
    }
    
    public String getUserID() {
        return userID;
    }
    
    public void setUserID(String userID) {
        this.userID = userID;
    }
    
    public Integer getPlatformID() {
        return platformID;
    }
    
    public void setPlatformID(Integer platformID) {
        this.platformID = platformID;
    }

    public String getSessionID() {
        return sessionID;
    }

    public void setSessionID(String sessionID) {
        this.sessionID = sessionID;
    }

    public String getDeviceID() {
        return deviceID;
    }

    public void setDeviceID(String deviceID) {
        this.deviceID = deviceID;
    }

    public String getTenantID() {
        return tenantID;
    }

    public void setTenantID(String tenantID) {
        this.tenantID = tenantID;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public Long getTokenVersion() {
        return tokenVersion;
    }

    public void setTokenVersion(Long tokenVersion) {
        this.tokenVersion = tokenVersion;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public Channel getChannel() {
        return channel;
    }
    
    public void setChannel(Channel channel) {
        this.channel = channel;
    }
    
    public String getToken() {
        return token;
    }
    
    public void setToken(String token) {
        this.token = token;
    }
    
    public Long getConnectTime() {
        return connectTime;
    }
    
    public void setConnectTime(Long connectTime) {
        this.connectTime = connectTime;
    }
    
    public Long getLastActiveTime() {
        return lastActiveTime;
    }
    
    public void setLastActiveTime(Long lastActiveTime) {
        this.lastActiveTime = lastActiveTime;
    }
    
    public boolean isAuthenticated() {
        return authenticated;
    }
    
    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }
    
    public String getClientIP() {
        return clientIP;
    }
    
    public void setClientIP(String clientIP) {
        this.clientIP = clientIP;
    }
    
    public String getUserAgent() {
        return userAgent;
    }
    
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
    
    public int getStatus() {
        return status;
    }
    
    public void setStatus(int status) {
        this.status = status;
    }

    public ConnectionContext getContext() {
        return context;
    }

    public void setContext(ConnectionContext context) {
        this.context = context;
    }
    
    public long getHeartbeatCount() {
        return heartbeatCount.get();
    }
    
    public long getSendMsgCount() {
        return sendMsgCount.get();
    }
    
    public long getRecvMsgCount() {
        return recvMsgCount.get();
    }
    
    @Override
    public String toString() {
        return "UserConnection{" +
                "connectionID='" + connectionID + '\'' +
                ", userID='" + userID + '\'' +
                ", platformID=" + platformID +
                ", sessionID='" + sessionID + '\'' +
                ", deviceID='" + deviceID + '\'' +
                ", authenticated=" + authenticated +
                ", clientIP='" + clientIP + '\'' +
                ", status=" + status +
                ", connectTime=" + connectTime +
                ", lastActiveTime=" + lastActiveTime +
                ", heartbeatCount=" + heartbeatCount.get() +
                ", sendMsgCount=" + sendMsgCount.get() +
                ", recvMsgCount=" + recvMsgCount.get() +
                '}';
    }
}
