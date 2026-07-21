package com.cheeseocean.im.postoffice.connection;

import com.cheeseocean.im.common.api.dto.route.RouteSnapshot;
import com.cheeseocean.im.common.api.enums.PlatformType;
import com.cheeseocean.im.common.api.protocol.ProtoEnvelopeMapper;
import com.cheeseocean.im.common.api.protocol.ServerEnvelope;
import com.cheeseocean.im.postoffice.config.NodeIdentityProvider;
import com.cheeseocean.im.postoffice.config.ServerProperties;
import com.cheeseocean.im.postoffice.dedup.DeliveryDedupStore;
import com.cheeseocean.im.postoffice.kickoff.NodeCommandPublisher;
import com.cheeseocean.im.postoffice.login.LoginLease;
import com.cheeseocean.im.postoffice.login.LoginLeaseClaim;
import com.cheeseocean.im.postoffice.login.LoginLeaseRenewal;
import com.cheeseocean.im.postoffice.login.LoginLeaseStore;
import com.cheeseocean.im.postoffice.service.OnlineRouteService;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import com.cheeseocean.im.common.core.logging.CommonLoggers;
import org.slf4j.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Tracks authenticated gateway connections and pushes messages to active devices.
 */
@Component
public class ConnectionManager {
    
    private static final Logger logger = CommonLoggers.POSTOFFICE;

    /**
     * 用户维度分片锁数量。连接注册/移除的热点是同一用户多端重连，不需要全局串行。
     */
    private static final int USER_LOCK_SHARDS = 64;

    /**
     * 未认证连接没有 userId，用 connectionId 分片保护 pending 索引。
     */
    private static final int CONNECTION_LOCK_SHARDS = 64;
    
    private final OnlineRouteService onlineRouteService;

    /**
     * 投递去重存储。生产环境由 {@link com.cheeseocean.im.postoffice.dedup.RedisDeliveryDedupStore}
     * 提供 Redis 跨节点 claim/commit/abort + TTL 自动过期；测试环境不注入时走 NO-OP（直接放行）回退，
     * 因为单元测试不连 Redis，重复投递的副作用由测试断言决定而非依赖真实去重。
     *
     * <p>禁止再换一份本地 Set 来"修复"无界增长问题（见 ASSESSMENT P0-5 + 根 AGENTS §8）。
     */
    private final DeliveryDedupStore deliveryDedupStore;

    private final NodeIdentityProvider nodeIdentityProvider;
    private final int maxConnections;
    private final int maxConnectionsPerUser;
    private final boolean loginLeaseEnforced;
    private final LoginLeaseStore loginLeaseStore;
    private final NodeCommandPublisher nodeCommandPublisher;
    
    /**
     * Connection ID to connection metadata.
     */
    private final Map<String, UserConnection> connectionMap = new ConcurrentHashMap<>();
    
    /**
     * User ID to active connection IDs.
     */
    private final Map<String, Set<String>> userConnectionMap = new ConcurrentHashMap<>();

    /**
     * Session ID to active authenticated connection IDs.
     */
    private final Map<String, Set<String>> sessionConnectionMap = new ConcurrentHashMap<>();

    /**
     * userId:deviceId to active authenticated connection IDs.
     */
    private final Map<String, Set<String>> deviceConnectionMap = new ConcurrentHashMap<>();
    
    /**
     * Netty channel to connection ID.
     */
    private final Map<Channel, String> channelConnectionMap = new ConcurrentHashMap<>();

    /**
     * Distinct online-user count.
     */
    private final AtomicLong onlineUserCount = new AtomicLong(0);
    
    /**
     * Total active connection count.
     */
    private final AtomicLong totalConnectionCount = new AtomicLong(0);
    private final AtomicLong rejectedConnectionCount = new AtomicLong(0);
    private final AtomicLong unwritableWriteCount = new AtomicLong(0);

    private final ReentrantLock[] userLocks = createLocks(USER_LOCK_SHARDS);

    private final ReentrantLock[] connectionLocks = createLocks(CONNECTION_LOCK_SHARDS);
    
    /**
     * Active multi-device conflict policy.
     */
    private MultiLoginStrategy multiLoginStrategy = MultiLoginStrategy.SAME_TERMINAL_KICK;
    
    /**
     * Connection timeout in milliseconds.
     */
    private long connectionTimeoutMs = 5 * 60 * 1000;
    
    /**
     * Background scheduler for cleanup and metrics updates.
     */
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public ConnectionManager(ObjectProvider<OnlineRouteService> onlineRouteServiceProvider,
                             ObjectProvider<DeliveryDedupStore> deliveryDedupStoreProvider,
                             NodeIdentityProvider nodeIdentityProvider,
                             ServerProperties serverProperties,
                             ObjectProvider<LoginLeaseStore> loginLeaseStoreProvider,
                             ObjectProvider<NodeCommandPublisher> nodeCommandPublisherProvider) {
        this.onlineRouteService = onlineRouteServiceProvider.getIfAvailable();
        this.deliveryDedupStore = deliveryDedupStoreProvider.getIfAvailable();
        this.nodeIdentityProvider = nodeIdentityProvider;
        ServerProperties.ConnectionConfig config = serverProperties.getConnection();
        this.multiLoginStrategy = resolveMultiLoginStrategy(config.getMultiLoginStrategy());
        this.connectionTimeoutMs = Math.max(1_000L, config.getTimeoutMs());
        this.maxConnections = Math.max(1, config.getMaxConnections());
        this.maxConnectionsPerUser = Math.max(1, config.getMaxConnectionsPerUser());
        this.loginLeaseEnforced = serverProperties.getLoginLease().isEnforce();
        this.loginLeaseStore = loginLeaseStoreProvider.getIfAvailable();
        this.nodeCommandPublisher = nodeCommandPublisherProvider.getIfAvailable();
        if (loginLeaseEnforced && loginLeaseStore == null) {
            throw new IllegalStateException("login lease enforce requires Redis LoginLeaseStore");
        }
    }
    
    /**
     * 初始化连接管理器
     */
    @PostConstruct
    public void init() {
        // 启动定时清理任务，每分钟执行一次
        scheduler.scheduleAtFixedRate(this::cleanupTimeoutConnections, 1, 1, TimeUnit.MINUTES);
        
        // 启动统计任务，每30秒执行一次
        scheduler.scheduleAtFixedRate(this::updateStatistics, 30, 30, TimeUnit.SECONDS);
        
        logger.info("ConnectionManager initialized, multiLoginStrategy={}, timeoutMs={}, maxConnections={}, maxConnectionsPerUser={}",
                multiLoginStrategy.getName(), connectionTimeoutMs, maxConnections, maxConnectionsPerUser);
    }
    
    /**
     * 注册未认证连接，仅建立 connectionId 和 channel 的索引。
     */
    public boolean registerPendingConnection(UserConnection connection) {
        ReentrantLock lock = connectionLock(connection == null ? null : connection.getConnectionID());
        lock.lock();
        try {
            String connectionID = connection.getConnectionID();
            if (connectionID == null || connection.getChannel() == null) {
                logger.warn("Pending connection is missing required fields: connectionID={}, channel={}",
                        connectionID, connection.getChannel());
                return false;
            }

            UserConnection existing = connectionMap.get(connectionID);
            if (existing != null && existing != connection) {
                logger.warn("Pending connection already exists: {}", connectionID);
                return false;
            }
            if (existing == connection) {
                return true;
            }
            if (!tryAcquireConnectionSlot()) {
                rejectedConnectionCount.incrementAndGet();
                logger.warn("Node connection limit reached: maxConnections={}, remoteAddress={}",
                        maxConnections, connection.getChannel().remoteAddress());
                return false;
            }

            try {
                connectionMap.put(connectionID, connection);
                channelConnectionMap.put(connection.getChannel(), connectionID);
            } catch (RuntimeException exception) {
                totalConnectionCount.decrementAndGet();
                throw exception;
            }

            logger.info("Pending connection registered: connectionID={}, remoteAddress={}",
                    connectionID, connection.getChannel().remoteAddress());
            return true;
        } catch (Exception e) {
            logger.error("Failed to register pending connection: {}",
                    connection == null ? null : connection.getConnectionID(), e);
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 添加新连接
     */
    public boolean addConnection(UserConnection connection) {
        ReentrantLock connectionLock = connectionLock(connection == null ? null : connection.getConnectionID());
        ReentrantLock userLock = userLock(connection == null ? null : connection.getUserID());
        List<UserConnection> connectionsToKick = List.of();
        List<LoginLease> globallyEvicted = List.of();
        boolean added = false;
        boolean leaseClaimed = false;
        connectionLock.lock();
        userLock.lock();
        try {
            String connectionID = connection.getConnectionID();
            String userID = connection.getUserID();
            
            // 检查连接是否已存在
            UserConnection existingConnection = connectionMap.get(connectionID);
            if (existingConnection != null && existingConnection != connection) {
                logger.warn("Connection already exists: {}", connectionID);
                return false;
            }
            
            // 获取用户现有连接
            List<UserConnection> existingConnections = getUserConnections(userID);

            if (loginLeaseEnforced) {
                LoginLeaseClaim claim = loginLeaseStore.claim(
                        toLoginLease(connection),
                        multiLoginStrategy,
                        maxConnectionsPerUser);
                if (!claim.accepted()) {
                    rejectedConnectionCount.incrementAndGet();
                    logger.warn("Global per-user connection limit reached: userID={}, maxConnectionsPerUser={}",
                            userID, maxConnectionsPerUser);
                    return false;
                }
                connection.setLoginLeaseGeneration(claim.generation());
                globallyEvicted = claim.evicted();
                leaseClaimed = true;
            } else {
                // 未开启全局 lease 时保留节点本地策略，供 all-in-one 与两阶段滚动升级 shadow 期使用。
                List<UserConnection> kickCandidates = multiLoginStrategy.getConnectionsToKick(
                        connection, existingConnections);
                connectionsToKick = kickCandidates;
                long remainingConnections = existingConnections.stream()
                        .filter(existing -> !kickCandidates.contains(existing))
                        .count();
                if (remainingConnections >= maxConnectionsPerUser) {
                    rejectedConnectionCount.incrementAndGet();
                    logger.warn("Per-user connection limit reached: userID={}, maxConnectionsPerUser={}",
                            userID, maxConnectionsPerUser);
                    return false;
                }
            }
            
            // 添加或提升连接
            connectionMap.put(connectionID, connection);
            channelConnectionMap.put(connection.getChannel(), connectionID);
            
            // 更新用户连接映射
            userConnectionMap.computeIfAbsent(userID, k -> ConcurrentHashMap.newKeySet())
                             .add(connectionID);

            if (connection.getSessionId() != null && !connection.getSessionId().isBlank()) {
                sessionConnectionMap.computeIfAbsent(connection.getSessionId(), k -> ConcurrentHashMap.newKeySet())
                        .add(connectionID);
            }

            String deviceKey = buildDeviceKey(connection.getUserID(), connection.getDeviceId());
            if (deviceKey != null) {
                deviceConnectionMap.computeIfAbsent(deviceKey, k -> ConcurrentHashMap.newKeySet())
                        .add(connectionID);
            }
            
            // 更新计数器
            if (getUserConnections(userID).size() == 1) {
                onlineUserCount.incrementAndGet();
            }
            
            registerOnlineRoute(connection);
            
            logger.info("Connection added: userID={}, connectionID={}, platform={}, total={}", 
                       userID, connectionID, connection.getPlatformName(), totalConnectionCount.get());
            added = true;
            
        } catch (Exception e) {
            logger.error("Failed to add connection: {}",
                    connection == null ? null : connection.getConnectionID(), e);
            if (connection != null && connectionMap.get(connection.getConnectionID()) == connection) {
                removeConnectionLocked(connection.getConnectionID());
            }
            return false;
        } finally {
            if (leaseClaimed && !added) {
                releaseLoginLease(connection);
            }
            userLock.unlock();
            connectionLock.unlock();
        }
        if (loginLeaseEnforced && !dispatchEvictedConnections(globallyEvicted)) {
            releaseLoginLease(connection);
            removeConnection(connection.getConnectionID());
            logger.error("Global login victim dispatch failed; rejecting new connection: userID={}, connectionID={}",
                    connection.getUserID(), connection.getConnectionID());
            return false;
        }
        // 旧连接踢下线会再次进入 removeConnection。放在分片锁外执行，避免与并发断线形成 connection -> user / user -> connection 反向等待。
        for (UserConnection connToKick : connectionsToKick) {
            kickConnection(connToKick, "新设备登录，当前连接被踢下线");
        }
        return added;
    }
    
    /**
     * 移除连接
     */
    public boolean removeConnection(String connectionID) {
        ReentrantLock connectionLock = connectionLock(connectionID);
        connectionLock.lock();
        try {
            UserConnection current = connectionMap.get(connectionID);
            if (current == null) {
                return false;
            }
            ReentrantLock userLock = current.getUserID() == null ? null : userLock(current.getUserID());
            if (userLock != null) {
                userLock.lock();
            }
            try {
                return removeConnectionLocked(connectionID);
            } finally {
                if (userLock != null) {
                    userLock.unlock();
                }
            }
        } catch (Exception e) {
            logger.error("Failed to remove connection: {}", connectionID, e);
            return false;
        } finally {
            connectionLock.unlock();
        }
    }

    private boolean removeConnectionLocked(String connectionID) {
        UserConnection connection = connectionMap.remove(connectionID);
            if (connection == null) {
                return false;
            }
            
            String userID = connection.getUserID();
            
            // 移除Channel映射
            channelConnectionMap.remove(connection.getChannel());
            
            // 更新用户连接映射
            if (userID != null) {
                Set<String> userConnections = userConnectionMap.get(userID);
                if (userConnections != null) {
                    userConnections.remove(connectionID);
                    if (userConnections.isEmpty()) {
                        userConnectionMap.remove(userID);
                        onlineUserCount.decrementAndGet();
                    }
                }
            }

            if (connection.getSessionId() != null) {
                removeIndex(sessionConnectionMap, connection.getSessionId(), connectionID);
            }

            String deviceKey = buildDeviceKey(connection.getUserID(), connection.getDeviceId());
            if (deviceKey != null) {
                removeIndex(deviceConnectionMap, deviceKey, connectionID);
            }
            
            // 更新计数器
            totalConnectionCount.decrementAndGet();
            
            if (userID != null) {
                unregisterOnlineRoute(connection);
            }
            releaseLoginLease(connection);
            
            logger.info("Connection removed: userID={}, connectionID={}, platform={}, total={}", 
                       userID, connectionID, connection.getPlatformName(), totalConnectionCount.get());
            
            return true;
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

    public List<UserConnection> getSessionConnections(String sessionID) {
        return getIndexedConnections(sessionConnectionMap.get(sessionID));
    }

    public List<UserConnection> getDeviceConnections(String userID, String deviceID) {
        String deviceKey = buildDeviceKey(userID, deviceID);
        return deviceKey == null ? new ArrayList<>() : getIndexedConnections(deviceConnectionMap.get(deviceKey));
    }

    public void kickUserConnections(String userID, String reason) {
        for (UserConnection connection : getUserConnections(userID)) {
            kickConnection(connection, reason);
        }
    }

    public void kickSessionConnections(String sessionID, String reason) {
        for (UserConnection connection : getSessionConnections(sessionID)) {
            kickConnection(connection, reason);
        }
    }

    public void kickDeviceConnections(String userID, String deviceID, String reason) {
        for (UserConnection connection : getDeviceConnections(userID, deviceID)) {
            kickConnection(connection, reason);
        }
    }

    /**
     * 仅踢指定 connectionId；迟到命令找不到旧连接时保持 NOOP，绝不降级到 device/user 范围。
     */
    public void kickConnectionById(String connectionId, String reason) {
        kickConnectionById(connectionId, null, reason);
    }

    /**
     * generation 存在时执行 fencing 校验，迟到的旧命令不能关闭复用同一目标的新 lease。
     */
    public void kickConnectionById(String connectionId, Long generation, String reason) {
        UserConnection connection = getConnection(connectionId);
        if (connection != null && (generation == null
                || generation.equals(connection.getLoginLeaseGeneration()))) {
            kickConnection(connection, reason);
        }
    }

    public DeliveryDedupStore.Claim claimDelivery(String deliveryId, String userId, String deviceId) {
        if (deliveryDedupStore == null) {
            return DeliveryDedupStore.Claim.acquired("noop", UUID.randomUUID().toString());
        }
        return deliveryDedupStore.claim(deliveryId, userId, deviceId);
    }

    public boolean commitDelivery(DeliveryDedupStore.Claim claim) {
        return deliveryDedupStore == null || deliveryDedupStore.commit(claim);
    }

    public boolean abortDelivery(DeliveryDedupStore.Claim claim) {
        return deliveryDedupStore == null || deliveryDedupStore.abort(claim);
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
     * 返回当前连接快照，供节点级 lease 续租等批处理使用；调用方不得修改内部索引。
     */
    public List<UserConnection> snapshotConnections() {
        return List.copyOf(connectionMap.values());
    }
    
    /**
     * 踢掉连接
     */
    public void kickConnection(UserConnection connection, String reason) {
        try {
            // 发送强制下线通知
            sendMessageToConnection(connection, ServerEnvelope.forceLogout("system", reason));
            
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
    public boolean sendMessageToConnection(UserConnection connection, ServerEnvelope envelope) {
        return writeMessageToConnection(connection, envelope) != null;
    }

    /**
     * 发起底层 Channel 写入。
     *
     * <p>返回非空只表示获得了可观察的写入 future；调用方若需要投递终态，必须检查 future，
     * 不能把 writeAndFlush 没有同步抛异常当作成功。</p>
     */
    public ChannelFuture writeMessageToConnection(UserConnection connection, ServerEnvelope envelope) {
        try {
            if (envelope == null || connection == null || connection.getChannel() == null
                    || !connection.getChannel().isActive()) {
                return null;
            }
            if (!connection.getChannel().isWritable()) {
                unwritableWriteCount.incrementAndGet();
                logger.debug("Skip write to unwritable channel: connectionID={}", connection.getConnectionID());
                return null;
            }

            ChannelFuture writeFuture;
            if ("TCP".equalsIgnoreCase(connection.getProtocol())) {
                writeFuture = connection.getChannel().writeAndFlush(envelope);
            } else {
                byte[] payload = ProtoEnvelopeMapper.toProto(envelope).toByteArray();
                writeFuture = connection.getChannel().writeAndFlush(new BinaryWebSocketFrame(
                        connection.getChannel().alloc().buffer(payload.length).writeBytes(payload)));
            }
            writeFuture.addListener(future -> {
                if (future.isSuccess()) {
                    connection.incrementSendMsg();
                }
            });
            return writeFuture;
            
        } catch (Exception e) {
            logger.error("Failed to send message to connection: {}", connection.getConnectionID(), e);
            return null;
        }
    }

    /**
     * 向用户的所有连接发送消息
     */
    public int sendMessageToUser(String userID, ServerEnvelope envelope) {
        List<UserConnection> connections = getUserConnections(userID);
        int successCount = 0;
        
        for (UserConnection connection : connections) {
            if (sendMessageToConnection(connection, envelope)) {
                successCount++;
            }
        }
        
        return successCount;
    }
    
    /**
     * 广播消息给所有在线用户
     */
    public int broadcastMessage(ServerEnvelope envelope) {
        int successCount = 0;
        
        for (UserConnection connection : connectionMap.values()) {
            if (sendMessageToConnection(connection, envelope)) {
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
            logger.debug("Connection statistics: totalConnections={}, onlineUsers={}, rejectedConnections={}, unwritableWrites={}",
                    totalConnectionCount.get(), onlineUserCount.get(),
                    rejectedConnectionCount.get(), unwritableWriteCount.get());
            
            // 可以在这里添加更多统计信息的更新逻辑
            
        } catch (Exception e) {
            logger.error("Failed to update statistics", e);
        }
    }
    
    private void registerOnlineRoute(UserConnection connection) {
        String deviceId = routeDeviceId(connection);
        if (onlineRouteService == null || connection.getUserID() == null || deviceId == null) {
            return;
        }
        RouteSnapshot snapshot = new RouteSnapshot();
        snapshot.setUserId(connection.getUserID());
        snapshot.setConnectionId(connection.getConnectionID());
        snapshot.setSessionId(connection.getSessionId());
        snapshot.setDeviceId(deviceId);
        snapshot.setPlatformId(connection.getPlatformType() == null
                ? null
                : connection.getPlatformType().getCode());
        if (connection.getLoginLeaseGeneration() != null) {
            snapshot.setLoginLeaseVersion(RouteSnapshot.LOGIN_LEASE_VERSION_1);
            snapshot.setLoginLeaseGeneration(connection.getLoginLeaseGeneration());
        }
        snapshot.setGatewayNode(nodeIdentityProvider.getNodeId());
        snapshot.setDeliveryOutcomeVersion(RouteSnapshot.DELIVERY_OUTCOME_VERSION_1);
        snapshot.setConnectedAt(connection.getConnectTime());
        snapshot.setHeartbeatAt(connection.getLastActiveTime());
        onlineRouteService.register(snapshot);
    }

    private void unregisterOnlineRoute(UserConnection connection) {
        String deviceId = routeDeviceId(connection);
        if (onlineRouteService == null || connection.getUserID() == null || deviceId == null) {
            return;
        }
        onlineRouteService.unregister(connection.getUserID(), deviceId, connection.getConnectionID());
    }
    
    // ============ Getter and Setter ============
    
    public long getOnlineUserCount() {
        return onlineUserCount.get();
    }
    
    public long getTotalConnectionCount() {
        return totalConnectionCount.get();
    }

    public long getRejectedConnectionCount() {
        return rejectedConnectionCount.get();
    }

    public long getUnwritableWriteCount() {
        return unwritableWriteCount.get();
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
    @PreDestroy
    public void destroy() {
        if (loginLeaseEnforced && loginLeaseStore != null) {
            List<LoginLeaseRenewal> renewals = connectionMap.values().stream()
                    .map(this::toRenewal)
                    .filter(Objects::nonNull)
                    .toList();
            try {
                loginLeaseStore.releaseBatch(renewals);
            } catch (RuntimeException exception) {
                logger.warn("Failed to release login leases during shutdown; leases will expire by TTL", exception);
            }
        }
        scheduler.shutdown();
        connectionMap.clear();
        userConnectionMap.clear();
        sessionConnectionMap.clear();
        deviceConnectionMap.clear();
        channelConnectionMap.clear();
        logger.info("ConnectionManager destroyed");
    }

    private boolean tryAcquireConnectionSlot() {
        long current;
        do {
            current = totalConnectionCount.get();
            if (current >= maxConnections) {
                return false;
            }
        } while (!totalConnectionCount.compareAndSet(current, current + 1));
        return true;
    }

    private MultiLoginStrategy resolveMultiLoginStrategy(String configured) {
        if (configured == null || configured.isBlank()) {
            return MultiLoginStrategy.SAME_TERMINAL_KICK;
        }
        try {
            return MultiLoginStrategy.valueOf(configured.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Unknown multi login strategy: " + configured, exception);
        }
    }

    private LoginLease toLoginLease(UserConnection connection) {
        PlatformType platformType = connection.getPlatformType();
        return new LoginLease(
                Objects.toString(connection.getTenantId(), "default"),
                connection.getUserID(),
                connection.getConnectionID(),
                0L,
                nodeIdentityProvider.getNodeId(),
                routeDeviceId(connection),
                platformType == null ? PlatformType.UNKNOWN.getCode() : platformType.getCode(),
                platformClass(platformType),
                connection.getSessionId(),
                0L);
    }

    private String platformClass(PlatformType platformType) {
        if (platformType == null) {
            return "UNKNOWN";
        }
        if (platformType.isMobile()) {
            return "MOBILE";
        }
        if (platformType.isPc()) {
            return "PC";
        }
        if (platformType.isWeb()) {
            return "WEB";
        }
        return "UNKNOWN";
    }

    private boolean dispatchEvictedConnections(List<LoginLease> evicted) {
        for (LoginLease victim : evicted) {
            if (nodeIdentityProvider.getNodeId().equals(victim.gatewayNode())) {
                kickConnectionById(victim.connectionId(), victim.generation(), "新连接取得全局登录租约");
                continue;
            }
            if (nodeCommandPublisher == null) {
                return false;
            }
            com.cheeseocean.im.common.api.dto.user.KickoffCommand command =
                    new com.cheeseocean.im.common.api.dto.user.KickoffCommand();
            command.setUserId(victim.userId());
            command.setSessionId(victim.sessionId());
            command.setDeviceId(victim.deviceId());
            command.setConnectionId(victim.connectionId());
            command.setLoginLeaseGeneration(victim.generation());
            command.setReason("新连接取得全局登录租约");
            if (!nodeCommandPublisher.publishKickoff(victim.gatewayNode(), command)) {
                return false;
            }
        }
        return true;
    }

    private void releaseLoginLease(UserConnection connection) {
        LoginLeaseRenewal renewal = toRenewal(connection);
        if (!loginLeaseEnforced || loginLeaseStore == null || renewal == null) {
            return;
        }
        try {
            loginLeaseStore.release(renewal);
        } catch (RuntimeException exception) {
            logger.warn("Failed to release login lease; lease will expire by TTL: connectionID={}",
                    connection.getConnectionID(), exception);
        }
    }

    private LoginLeaseRenewal toRenewal(UserConnection connection) {
        if (connection == null || connection.getLoginLeaseGeneration() == null
                || connection.getUserID() == null || connection.getConnectionID() == null) {
            return null;
        }
        return new LoginLeaseRenewal(
                Objects.toString(connection.getTenantId(), "default"),
                connection.getUserID(),
                connection.getConnectionID(),
                connection.getLoginLeaseGeneration());
    }

    private List<UserConnection> getIndexedConnections(Set<String> connectionIDs) {
        if (connectionIDs == null || connectionIDs.isEmpty()) {
            return new ArrayList<>();
        }
        return connectionIDs.stream()
                .map(connectionMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private void removeIndex(Map<String, Set<String>> indexMap, String key, String connectionID) {
        Set<String> ids = indexMap.get(key);
        if (ids == null) {
            return;
        }
        ids.remove(connectionID);
        if (ids.isEmpty()) {
            indexMap.remove(key);
        }
    }

    private String buildDeviceKey(String userID, String deviceID) {
        if (userID == null || userID.isBlank() || deviceID == null || deviceID.isBlank()) {
            return null;
        }
        return userID + ":" + deviceID;
    }

    public static String routeDeviceId(UserConnection connection) {
        if (connection == null) {
            return null;
        }
        if (connection.getDeviceId() != null && !connection.getDeviceId().isBlank()) {
            return connection.getDeviceId();
        }
        if (connection.getPlatformType() == null) {
            return null;
        }
        return connection.getPlatformName().toLowerCase() + "-" + connection.getPlatformType();
    }

    private static ReentrantLock[] createLocks(int shardCount) {
        ReentrantLock[] locks = new ReentrantLock[shardCount];
        for (int i = 0; i < shardCount; i++) {
            locks[i] = new ReentrantLock();
        }
        return locks;
    }

    private ReentrantLock userLock(String userID) {
        return lockFor(userLocks, userID);
    }

    private ReentrantLock connectionLock(String connectionID) {
        return lockFor(connectionLocks, connectionID);
    }

    private ReentrantLock lockFor(ReentrantLock[] locks, String key) {
        int hash = key == null ? 0 : key.hashCode();
        return locks[Math.floorMod(hash, locks.length)];
    }
}
