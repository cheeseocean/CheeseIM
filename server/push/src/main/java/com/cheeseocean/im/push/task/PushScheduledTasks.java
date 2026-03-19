package com.cheeseocean.im.push.task;

import com.cheeseocean.im.push.service.impl.DeviceTokenServiceImpl;
import com.cheeseocean.im.push.service.PushStatisticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled maintenance tasks for the push module.
 */
@Component
@EnableScheduling
@ConditionalOnExpression(
        "'${app.runtime.mode:standalone}' == 'standalone' && '${cheeseim.push.scheduled-tasks.enabled:true}' == 'true'"
)
public class PushScheduledTasks {
    
    private static final Logger logger = LoggerFactory.getLogger(PushScheduledTasks.class);
    
    @Autowired
    private DeviceTokenServiceImpl deviceTokenService;
    
    @Autowired
    private PushStatisticsService pushStatisticsService;
    
    /**
     * Cleans up expired device tokens.
     */
    @Scheduled(fixedRateString = "#{${cheeseim.push.device-token.cleanup-interval-hours:6} * 60 * 60 * 1000}")
    public void cleanupExpiredDeviceTokens() {
        try {
            logger.info("开始清理过期设备Token");
            
            long startTime = System.currentTimeMillis();
            int cleanedCount = deviceTokenService.cleanupExpiredTokens();
            long duration = System.currentTimeMillis() - startTime;
            
            logger.info("过期设备Token清理完成: cleanedCount={}, duration={}ms", cleanedCount, duration);
            
        } catch (Exception e) {
            logger.error("清理过期设备Token失败", e);
        }
    }
    
    /**
     * Logs aggregate device-token statistics.
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void statisticsDeviceTokens() {
        try {
            logger.debug("开始统计设备Token信息");
            
            DeviceTokenServiceImpl.DeviceTokenStats stats = deviceTokenService.getDeviceTokenStats();
            
            logger.info("设备Token统计: totalTokens={}, activeTokens={}, expiredTokens={}, platformDistribution={}", 
                       stats.getTotalTokens(), stats.getActiveTokens(), stats.getExpiredTokens(), 
                       stats.getPlatformDistribution());
            
        } catch (Exception e) {
            logger.error("统计设备Token信息失败", e);
        }
    }
    
    /**
     * Logs push delivery statistics.
     */
    @Scheduled(cron = "0 30 * * * ?")
    public void logPushStatistics() {
        try {
            logger.debug("开始输出推送统计信息");
            
            var pushStats = pushStatisticsService.getPushStatistics();
            logger.info("推送统计: totalPush={}, successPush={}, failedPush={}, successRate={}%", 
                       pushStats.getTotalPushCount(), pushStats.getSuccessPushCount(), 
                       pushStats.getFailedPushCount(), String.format("%.2f", pushStats.getSuccessRate()));
            
            var realtimeStats = pushStatisticsService.getRealtimePushStats();
            logger.info("实时统计: currentHourPush={}, currentMinutePush={}, currentSecondPush={}, currentSuccessRate={}%", 
                       realtimeStats.getCurrentHourPushCount(), realtimeStats.getCurrentMinutePushCount(),
                       realtimeStats.getCurrentSecondPushCount(), String.format("%.2f", realtimeStats.getCurrentSuccessRate()));
            
            var providerStats = pushStatisticsService.getProviderStatistics();
            for (var entry : providerStats.entrySet()) {
                var stats = entry.getValue();
                logger.info("提供商统计 [{}]: total={}, success={}, failed={}, successRate={}%", 
                           entry.getKey(), stats.getTotalCount(), stats.getSuccessCount(), 
                           stats.getFailedCount(), String.format("%.2f", stats.getSuccessRate()));
            }
            
            var platformStats = pushStatisticsService.getPlatformStatistics();
            for (var entry : platformStats.entrySet()) {
                var stats = entry.getValue();
                logger.info("平台统计 [{}]: total={}, success={}, failed={}, successRate={}%", 
                           stats.getPlatformName(), stats.getTotalCount(), stats.getSuccessCount(), 
                           stats.getFailedCount(), String.format("%.2f", stats.getSuccessRate()));
            }
            
        } catch (Exception e) {
            logger.error("输出推送统计信息失败", e);
        }
    }
    
    /**
     * Checks Redis-backed token storage and push statistics health.
     */
    @Scheduled(fixedRate = 300000)
    public void healthCheck() {
        try {
            logger.debug("开始推送服务健康检查");
            
            // 检查Redis连接
            DeviceTokenServiceImpl.DeviceTokenStats tokenStats = deviceTokenService.getDeviceTokenStats();
            if (tokenStats != null) {
                logger.debug("Redis连接正常: tokenStats={}", tokenStats);
            } else {
                logger.warn("Redis连接可能异常");
            }
            
            // 检查推送统计服务
            var pushStats = pushStatisticsService.getPushStatistics();
            if (pushStats != null) {
                logger.debug("推送统计服务正常: pushStats={}", pushStats);
            } else {
                logger.warn("推送统计服务可能异常");
            }
            
        } catch (Exception e) {
            logger.error("推送服务健康检查失败", e);
        }
    }
    
}
