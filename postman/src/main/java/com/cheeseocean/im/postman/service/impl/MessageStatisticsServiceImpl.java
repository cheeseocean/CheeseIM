package com.cheeseocean.im.postman.service.impl;

import com.cheeseocean.im.postman.service.MessageStatisticsService;
import com.cheeseocean.im.postman.service.OnlineUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 消息统计服务实现
 * 基于Redis和内存计数器实现消息统计
 * 
 * @author CheeseIM
 */
@Service
public class MessageStatisticsServiceImpl implements MessageStatisticsService {
    
    private static final Logger logger = LoggerFactory.getLogger(MessageStatisticsServiceImpl.class);
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private OnlineUserService onlineUserService;
    
    // Redis Key前缀
    private static final String STATS_KEY_PREFIX = "cheese_im:stats:";
    private static final String MESSAGE_TRANSFER_STATS_KEY = STATS_KEY_PREFIX + "message_transfer";
    private static final String REALTIME_STATS_KEY = STATS_KEY_PREFIX + "realtime";
    
    // 内存计数器（用于实时统计）
    private final LongAdder totalMessagesCounter = new LongAdder();
    private final LongAdder successMessagesCounter = new LongAdder();
    private final LongAdder failedMessagesCounter = new LongAdder();
    
    // 时间窗口计数器
    private final AtomicLong lastSecondMessages = new AtomicLong(0);
    private final AtomicLong lastMinuteMessages = new AtomicLong(0);
    private final AtomicLong lastHourMessages = new AtomicLong(0);
    private final AtomicLong lastSecondTimestamp = new AtomicLong(System.currentTimeMillis() / 1000);
    private final AtomicLong lastMinuteTimestamp = new AtomicLong(System.currentTimeMillis() / 60000);
    private final AtomicLong lastHourTimestamp = new AtomicLong(System.currentTimeMillis() / 3600000);
    
    @Override
    public void recordMessageTransfer(String messageType, Integer sessionType, boolean success) {
        try {
            // 更新总计数
            totalMessagesCounter.increment();
            if (success) {
                successMessagesCounter.increment();
            } else {
                failedMessagesCounter.increment();
            }
            
            // 更新时间窗口计数
            updateTimeWindowCounters();
            
            // 更新Redis统计
            String statsKey = MESSAGE_TRANSFER_STATS_KEY;
            
            // 更新总数
            redisTemplate.opsForHash().increment(statsKey, "totalMessages", 1);
            if (success) {
                redisTemplate.opsForHash().increment(statsKey, "successMessages", 1);
            } else {
                redisTemplate.opsForHash().increment(statsKey, "failedMessages", 1);
            }
            
            // 更新消息类型统计
            if (messageType != null) {
                redisTemplate.opsForHash().increment(statsKey, "messageType:" + messageType, 1);
            }
            
            // 更新会话类型统计
            if (sessionType != null) {
                redisTemplate.opsForHash().increment(statsKey, "sessionType:" + sessionType, 1);
            }
            
            // 更新最后更新时间
            redisTemplate.opsForHash().put(statsKey, "lastUpdateTime", System.currentTimeMillis());
            
            // 设置过期时间
            redisTemplate.expire(statsKey, 24, TimeUnit.HOURS);
            
            logger.debug("记录消息传输统计: messageType={}, sessionType={}, success={}", 
                        messageType, sessionType, success);
            
        } catch (Exception e) {
            logger.error("记录消息传输统计失败: messageType={}, sessionType={}, success={}", 
                        messageType, sessionType, success, e);
        }
    }
    
    @Override
    public void recordMessageRoute(String routeStrategy, int targetUserCount, boolean success) {
        try {
            // 更新路由策略统计
            String statsKey = MESSAGE_TRANSFER_STATS_KEY;
            
            if (routeStrategy != null) {
                redisTemplate.opsForHash().increment(statsKey, "routeStrategy:" + routeStrategy, 1);
            }
            
            // 更新目标用户数量统计
            redisTemplate.opsForHash().increment(statsKey, "totalTargetUsers", targetUserCount);
            
            // 更新路由成功/失败统计
            if (success) {
                redisTemplate.opsForHash().increment(statsKey, "routeSuccess", 1);
            } else {
                redisTemplate.opsForHash().increment(statsKey, "routeFailed", 1);
            }
            
            logger.debug("记录消息路由统计: routeStrategy={}, targetUserCount={}, success={}", 
                        routeStrategy, targetUserCount, success);
            
        } catch (Exception e) {
            logger.error("记录消息路由统计失败: routeStrategy={}, targetUserCount={}, success={}", 
                        routeStrategy, targetUserCount, success, e);
        }
    }
    
    @Override
    public MessageTransferStats getMessageTransferStats() {
        try {
            MessageTransferStats stats = new MessageTransferStats();
            String statsKey = MESSAGE_TRANSFER_STATS_KEY;
            
            Map<Object, Object> statsMap = redisTemplate.opsForHash().entries(statsKey);
            
            if (statsMap != null && !statsMap.isEmpty()) {
                // 基本统计
                stats.setTotalMessages(getLongValue(statsMap, "totalMessages"));
                stats.setSuccessMessages(getLongValue(statsMap, "successMessages"));
                stats.setFailedMessages(getLongValue(statsMap, "failedMessages"));
                
                // 计算成功率
                long total = stats.getTotalMessages();
                if (total > 0) {
                    stats.setSuccessRate((double) stats.getSuccessMessages() / total * 100);
                }
                
                // 消息类型统计
                Map<String, Long> messageTypeStats = new HashMap<>();
                Map<Integer, Long> sessionTypeStats = new HashMap<>();
                Map<String, Long> routeStrategyStats = new HashMap<>();
                
                for (Map.Entry<Object, Object> entry : statsMap.entrySet()) {
                    String key = entry.getKey().toString();
                    Long value = getLongValue(entry.getValue());
                    
                    if (key.startsWith("messageType:")) {
                        String messageType = key.substring("messageType:".length());
                        messageTypeStats.put(messageType, value);
                    } else if (key.startsWith("sessionType:")) {
                        String sessionTypeStr = key.substring("sessionType:".length());
                        try {
                            Integer sessionType = Integer.parseInt(sessionTypeStr);
                            sessionTypeStats.put(sessionType, value);
                        } catch (NumberFormatException e) {
                            logger.warn("无效的会话类型: {}", sessionTypeStr);
                        }
                    } else if (key.startsWith("routeStrategy:")) {
                        String routeStrategy = key.substring("routeStrategy:".length());
                        routeStrategyStats.put(routeStrategy, value);
                    }
                }
                
                stats.setMessageTypeStats(messageTypeStats);
                stats.setSessionTypeStats(sessionTypeStats);
                stats.setRouteStrategyStats(routeStrategyStats);
                
                // 最后更新时间
                Object lastUpdateTime = statsMap.get("lastUpdateTime");
                if (lastUpdateTime != null) {
                    stats.setLastUpdateTime(getLongValue(lastUpdateTime));
                }
            }
            
            return stats;
            
        } catch (Exception e) {
            logger.error("获取消息传输统计失败", e);
            return new MessageTransferStats();
        }
    }
    
    @Override
    public RealtimeStats getRealtimeStats() {
        try {
            RealtimeStats stats = new RealtimeStats();
            
            // 更新时间窗口计数
            updateTimeWindowCounters();
            
            // 设置实时统计
            stats.setMessagesPerSecond(lastSecondMessages.get());
            stats.setMessagesPerMinute(lastMinuteMessages.get());
            stats.setMessagesPerHour(lastHourMessages.get());
            
            // 获取在线用户统计
            OnlineUserService.OnlineUserStats onlineStats = onlineUserService.getOnlineUserStats();
            stats.setOnlineUsers(onlineStats.getTotalOnlineUsers());
            stats.setCurrentConnections(onlineStats.getTotalConnections());
            
            // TODO: 计算平均处理时间
            stats.setAvgProcessingTime(0.0);
            
            return stats;
            
        } catch (Exception e) {
            logger.error("获取实时统计失败", e);
            return new RealtimeStats();
        }
    }
    
    @Override
    public void resetStats() {
        try {
            // 重置内存计数器
            totalMessagesCounter.reset();
            successMessagesCounter.reset();
            failedMessagesCounter.reset();
            
            lastSecondMessages.set(0);
            lastMinuteMessages.set(0);
            lastHourMessages.set(0);
            
            // 删除Redis统计
            redisTemplate.delete(MESSAGE_TRANSFER_STATS_KEY);
            redisTemplate.delete(REALTIME_STATS_KEY);
            
            logger.info("统计信息已重置");
            
        } catch (Exception e) {
            logger.error("重置统计信息失败", e);
        }
    }
    
    /**
     * 更新时间窗口计数器
     */
    private void updateTimeWindowCounters() {
        long currentTime = System.currentTimeMillis();
        long currentSecond = currentTime / 1000;
        long currentMinute = currentTime / 60000;
        long currentHour = currentTime / 3600000;
        
        // 更新秒级计数
        if (currentSecond != lastSecondTimestamp.get()) {
            lastSecondTimestamp.set(currentSecond);
            lastSecondMessages.set(0);
        }
        lastSecondMessages.incrementAndGet();
        
        // 更新分钟级计数
        if (currentMinute != lastMinuteTimestamp.get()) {
            lastMinuteTimestamp.set(currentMinute);
            lastMinuteMessages.set(0);
        }
        lastMinuteMessages.incrementAndGet();
        
        // 更新小时级计数
        if (currentHour != lastHourTimestamp.get()) {
            lastHourTimestamp.set(currentHour);
            lastHourMessages.set(0);
        }
        lastHourMessages.incrementAndGet();
    }
    
    /**
     * 从Map中获取Long值
     */
    private long getLongValue(Map<Object, Object> map, String key) {
        Object value = map.get(key);
        return getLongValue(value);
    }
    
    /**
     * 转换为Long值
     */
    private long getLongValue(Object value) {
        if (value == null) {
            return 0L;
        }
        
        if (value instanceof Long) {
            return (Long) value;
        } else if (value instanceof Integer) {
            return ((Integer) value).longValue();
        } else if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                logger.warn("无效的数字格式: {}", value);
                return 0L;
            }
        }
        
        return 0L;
    }
}
