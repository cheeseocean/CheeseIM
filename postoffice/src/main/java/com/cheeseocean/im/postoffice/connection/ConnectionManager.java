package com.cheeseocean.im.postoffice.connection;

import com.cheeseocean.im.common.constants.MessageConstants;
import com.cheeseocean.im.postoffice.protocol.WSMessage;
import com.cheeseocean.im.postoffice.protocol.WSMessageType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 连接管理器
 * 参照OpenIM Server的连接管理实现，负责管理所有WebSocket连接
 * 
 * @author CheeseIM
 */
@Component
public class ConnectionManager {
    
    private static final Logger logger = LoggerFactory.getLogger(ConnectionManager.class);
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * 连接ID -> 连接映射
     */
    private final Map<String, UserConnection> connectionMap = new ConcurrentHashMap<>();
    
    /**
     * 用户ID -> 连接ID列表映射
     */
    private final Map<String, Set<String>> userConnectionMap = new ConcurrentHashMap<>();
    
    /**
     * Channel -> 连接ID映射
     */
    private final Map<Channel, String> channelConnectionMap = new ConcurrentHashMap<>();
    
    /**
     * 在线用户计数器
     */
    private final AtomicLong onlineUserCount = new AtomicLong(0);
    
    /**
     * 总连接数计数器
     */
    private final AtomicLong totalConnectionCount = new AtomicLong(0);
    
    /**
     * 多端登录策略，默认为同终端踢下线
     */
    private MultiLoginStrategy multiLoginStrategy = MultiLoginStrategy.SAME_TERMINAL_KICK;
    
    /**
     * 连接超时时间（毫秒），默认5分钟
     */
    private long connectionTimeoutMs = 5 * 60 * 1000;
    
    /**
     * 定时任务执行器
     */
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    /**
     * 初始化连接管理器
     */
    public void init() {
        // 启动定时清理任务，每分钟执行一次
        scheduler.scheduleAtFixedRate(this::cleanupTimeoutConnections, 1, 1, TimeUnit.MINUTES);
        
        // 启动统计任务，每30秒执行一次
        scheduler.scheduleAtFixedRate(this::updateStatistics, 30, 30, TimeUnit.SECONDS);
        
        logger.info("ConnectionManager initialized, multiLoginStrategy: {}, timeoutMs: {}", 
                   multiLoginStrategy.getName(), connectionTimeoutMs);
    }
    
    /**
     * 添加新连接
     */
    public synchronized boolean addConnection(UserConnection connection) {
        try {
            String connectionID = connection.getConnectionID();
            String userID = connection.getUserID();
            
            // 检查连接是否已存在
            if (connectionMap.containsKey(connectionID)) {
                logger.warn("Connection already exists: {}", connectionID);
                return false;
            }
            
            // 获取用户现有连接
            List<UserConnection> existingConnections = getUserConnections(userID);
            
            // 根据多端登录策略处理冲突
            List<UserConnection> connectionsToKick = multiLoginStrategy.getConnectionsToKick(
                    connection, existingConnections);
            
            // 踢掉需要踢掉的连接
            for (UserConnection connToKick : connectionsToKick) {
                kickConnection(connToKick, "新设备登录，当前连接被踢下线");
            }
            
            // 添加新连接
            connectionMap.put(connectionID, connection);
            channelConnectionMap.put(connection.getChannel(), connectionID);
            
            // 更新用户连接映射
            userConnectionMap.computeIfAbsent(userID, k -> ConcurrentHashMap.newKeySet())
                             .add(connectionID);
            
            // 更新计数器
            totalConnectionCount.incrementAndGet();
            if (getUserConnections(userID).size() == 1) {
                onlineUserCount.incrementAndGet();
            }
            
            // 同步到Redis
            syncConnectionToRedis(connection);
            
            logger.info("Connection added: userID={}, connectionID={}, platform={}, total={}", 
                       userID, connectionID, connection.getPlatformName(), totalConnectionCount.get());
            
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to add connection: {}", connection.getConnectionID(), e);
            return false;
        }
    }
    
    /**
     * 移除连接
     */
    public synchronized boolean removeConnection(String connectionID) {
        try {
            UserConnection connection = connectionMap.remove(connectionID);
            if (connection == null) {
                return false;
            }
            
            String userID = connection.getUserID();
            
            // 移除Channel映射
            channelConnectionMap.remove(connection.getChannel());
            
            // 更新用户连接映射
            Set<String> userConnections = userConnectionMap.get(userID);
            if (userConnections != null) {
                userConnections.remove(connectionID);
                if (userConnections.isEmpty()) {
                    userConnectionMap.remove(userID);
                    onlineUserCount.decrementAndGet();
                }
            }
            
            // 更新计数器
            totalConnectionCount.decrementAndGet();
            
            // 从Redis移除
            removeConnectionFromRedis(connection);
            
            logger.info("Connection removed: userID={}, connectionID={}, platform={}, total={}", 
                       userID, connectionID, connection.getPlatformName(), totalConnectionCount.get());
            
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to remove connection: {}", connectionID, e);
            return false;
        }
    }
    
    /**
     * 根据Channel移除连接
     */
    public boolean removeConnectionByChannel(Channel channel) {
        String connectionID = channelConnectionMap.get(channel);
        if (connectionID != null) {
            return removeConnection(connectionID);
        }
        return false;
    }
    
    /**
     * 获取连接
     */
    public UserConnection getConnection(String connectionID) {
        return connectionMap.get(connectionID);
    }
    
    /**
     * 根据Channel获取连接
     */
    public UserConnection getConnectionByChannel(Channel channel) {
        String connectionID = channelConnectionMap.get(channel);
        return connectionID != null ? connectionMap.get(connectionID) : null;
    }
    
    /**
     * 获取用户的所有连接
     */
    public List<UserConnection> getUserConnections(String userID) {
        Set<String> connectionIDs = userConnectionMap.get(userID);
        if (connectionIDs == null || connectionIDs.isEmpty()) {
            return new ArrayList<>();
        }
        
        return connectionIDs.stream()
                .map(connectionMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
    
    /**
     * 检查用户是否在线
     */
    public boolean isUserOnline(String userID) {
        return userConnectionMap.containsKey(userID) && !getUserConnections(userID).isEmpty();
    }
    
    /**
     * 获取在线用户列表
     */
    public Set<String> getOnlineUsers() {
        return new HashSet<>(userConnectionMap.keySet());
    }
    
    /**
     * 踢掉连接
     */
    public void kickConnection(UserConnection connection, String reason) {
        try {
            // 发送强制下线通知
            WSMessage forceLogoutMsg = WSMessage.forceLogoutNotify("system", reason);
            sendMessageToConnection(connection, forceLogoutMsg);
            
            // 关闭连接
            if (connection.getChannel() != null && connection.getChannel().isActive()) {
                connection.getChannel().close();
            }
            
            // 移除连接
            removeConnection(connection.getConnectionID());
            
            logger.info("Connection kicked: userID={}, connectionID={}, reason={}", 
                       connection.getUserID(), connection.getConnectionID(), reason);
            
        } catch (Exception e) {
            logger.error("Failed to kick connection: {}", connection.getConnectionID(), e);
        }
    }
    
    /**
     * 向连接发送消息
     */
    public boolean sendMessageToConnection(UserConnection connection, WSMessage message) {
        try {
            if (connection == null || connection.getChannel() == null || !connection.getChannel().isActive()) {
                return false;
            }
            
            String messageJson = objectMapper.writeValueAsString(message);
            connection.getChannel().writeAndFlush(new TextWebSocketFrame(messageJson));
            connection.incrementSendMsg();
            
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to send message to connection: {}", connection.getConnectionID(), e);
            return false;
        }
    }
    
    /**
     * 向用户的所有连接发送消息
     */
    public int sendMessageToUser(String userID, WSMessage message) {
        List<UserConnection> connections = getUserConnections(userID);
        int successCount = 0;
        
        for (UserConnection connection : connections) {
            if (sendMessageToConnection(connection, message)) {
                successCount++;
            }
        }
        
        return successCount;
    }
    
    /**
     * 广播消息给所有在线用户
     */
    public int broadcastMessage(WSMessage message) {
        int successCount = 0;
        
        for (UserConnection connection : connectionMap.values()) {
            if (sendMessageToConnection(connection, message)) {
                successCount++;
            }
        }
        
        return successCount;
    }
    
    /**
     * 清理超时连接
     */
    private void cleanupTimeoutConnections() {
        try {
            List<UserConnection> timeoutConnections = connectionMap.values().stream()
                    .filter(conn -> conn.isTimeout(connectionTimeoutMs))
                    .collect(Collectors.toList());
            
            for (UserConnection connection : timeoutConnections) {
                logger.warn("Connection timeout, removing: userID={}, connectionID={}, lastActive={}", 
                           connection.getUserID(), connection.getConnectionID(), 
                           new Date(connection.getLastActiveTime()));
                
                kickConnection(connection, "连接超时");
            }
            
            if (!timeoutConnections.isEmpty()) {
                logger.info("Cleaned up {} timeout connections", timeoutConnections.size());
            }
            
        } catch (Exception e) {
            logger.error("Failed to cleanup timeout connections", e);
        }
    }
    
    /**
     * 更新统计信息
     */
    private void updateStatistics() {
        try {
            logger.debug("Connection statistics: totalConnections={}, onlineUsers={}", 
                        totalConnectionCount.get(), onlineUserCount.get());
            
            // 可以在这里添加更多统计信息的更新逻辑
            
        } catch (Exception e) {
            logger.error("Failed to update statistics", e);
        }
    }
    
    /**
     * 同步连接信息到Redis
     */
    private void syncConnectionToRedis(UserConnection connection) {
        try {
            String key = MessageConstants.REDIS_KEY_USER_ONLINE + connection.getUserID();
            Map<String, Object> connectionInfo = Map.of(
                    "connectionID", connection.getConnectionID(),
                    "platformID", connection.getPlatformID(),
                    "connectTime", connection.getConnectTime(),
                    "lastActiveTime", connection.getLastActiveTime()
            );
            
            redisTemplate.opsForHash().putAll(key, connectionInfo);
            redisTemplate.expire(key, 30, TimeUnit.MINUTES);
            
        } catch (Exception e) {
            logger.error("Failed to sync connection to Redis: {}", connection.getConnectionID(), e);
        }
    }
    
    /**
     * 从Redis移除连接信息
     */
    private void removeConnectionFromRedis(UserConnection connection) {
        try {
            String key = MessageConstants.REDIS_KEY_USER_ONLINE + connection.getUserID();
            redisTemplate.delete(key);
            
        } catch (Exception e) {
            logger.error("Failed to remove connection from Redis: {}", connection.getConnectionID(), e);
        }
    }
    
    // ============ Getter and Setter ============
    
    public long getOnlineUserCount() {
        return onlineUserCount.get();
    }
    
    public long getTotalConnectionCount() {
        return totalConnectionCount.get();
    }
    
    public MultiLoginStrategy getMultiLoginStrategy() {
        return multiLoginStrategy;
    }
    
    public void setMultiLoginStrategy(MultiLoginStrategy multiLoginStrategy) {
        this.multiLoginStrategy = multiLoginStrategy;
        logger.info("MultiLoginStrategy changed to: {}", multiLoginStrategy.getName());
    }
    
    public long getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }
    
    public void setConnectionTimeoutMs(long connectionTimeoutMs) {
        this.connectionTimeoutMs = connectionTimeoutMs;
    }
    
    /**
     * 销毁连接管理器
     */
    public void destroy() {
        scheduler.shutdown();
        connectionMap.clear();
        userConnectionMap.clear();
        channelConnectionMap.clear();
        logger.info("ConnectionManager destroyed");
    }
}
