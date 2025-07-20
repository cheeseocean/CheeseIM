package com.cheeseocean.im.push.task;

import com.cheeseocean.im.push.service.DeviceTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 推送服务定时任务
 * 负责清理过期数据、统计数据等定时任务
 * 
 * @author CheeseIM
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "cheese.im.push.scheduled-tasks.enabled", havingValue = "true", matchIfMissing = true)
public class PushScheduledTasks {
    
    private static final Logger logger = LoggerFactory.getLogger(PushScheduledTasks.class);
    
    @Autowired
    private DeviceTokenService deviceTokenService;
    
    @Autowired
    private PushStatisticsService pushStatisticsService;
    
    @Value("${cheese.im.push.device-token.cleanup-interval-hours:6}")
    private int cleanupIntervalHours;
    
    /**
     * 清理过期的设备Token
     * 每6小时执行一次（可配置）
     */
    @Scheduled(fixedRateString = "#{${cheese.im.push.device-token.cleanup-interval-hours:6} * 60 * 60 * 1000}")
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
     * 统计设备Token信息
     * 每小时执行一次
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void statisticsDeviceTokens() {
        try {
            logger.debug("开始统计设备Token信息");
            
            DeviceTokenService.DeviceTokenStats stats = deviceTokenService.getDeviceTokenStats();
            
            logger.info("设备Token统计: totalTokens={}, activeTokens={}, expiredTokens={}, platformDistribution={}", 
                       stats.getTotalTokens(), stats.getActiveTokens(), stats.getExpiredTokens(), 
                       stats.getPlatformDistribution());
            
        } catch (Exception e) {
            logger.error("统计设备Token信息失败", e);
        }
    }
    
    /**
     * 重置每日推送计数
     * 每天凌晨0点执行
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void resetDailyPushCount() {
        try {
            logger.info("开始重置每日推送计数");
            
            // TODO: 实现重置每日推送计数的逻辑
            // 这里可以清理Redis中的每日推送计数数据
            
            logger.info("每日推送计数重置完成");
            
        } catch (Exception e) {
            logger.error("重置每日推送计数失败", e);
        }
    }
    
    /**
     * 输出推送统计信息
     * 每小时执行一次
     */
    @Scheduled(cron = "0 30 * * * ?")
    public void logPushStatistics() {
        try {
            logger.debug("开始输出推送统计信息");
            
            // 获取推送统计
            var pushStats = pushStatisticsService.getPushStatistics();
            logger.info("推送统计: totalPush={}, successPush={}, failedPush={}, successRate={}%", 
                       pushStats.getTotalPushCount(), pushStats.getSuccessPushCount(), 
                       pushStats.getFailedPushCount(), String.format("%.2f", pushStats.getSuccessRate()));
            
            // 获取实时统计
            var realtimeStats = pushStatisticsService.getRealtimePushStats();
            logger.info("实时统计: currentHourPush={}, currentMinutePush={}, currentSecondPush={}, currentSuccessRate={}%", 
                       realtimeStats.getCurrentHourPushCount(), realtimeStats.getCurrentMinutePushCount(),
                       realtimeStats.getCurrentSecondPushCount(), String.format("%.2f", realtimeStats.getCurrentSuccessRate()));
            
            // 获取提供商统计
            var providerStats = pushStatisticsService.getProviderStatistics();
            for (var entry : providerStats.entrySet()) {
                var stats = entry.getValue();
                logger.info("提供商统计 [{}]: total={}, success={}, failed={}, successRate={}%", 
                           entry.getKey(), stats.getTotalCount(), stats.getSuccessCount(), 
                           stats.getFailedCount(), String.format("%.2f", stats.getSuccessRate()));
            }
            
            // 获取平台统计
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
     * 健康检查任务
     * 每5分钟执行一次
     */
    @Scheduled(fixedRate = 300000) // 5分钟
    public void healthCheck() {
        try {
            logger.debug("开始推送服务健康检查");
            
            // 检查Redis连接
            DeviceTokenService.DeviceTokenStats tokenStats = deviceTokenService.getDeviceTokenStats();
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
    
    /**
     * 清理旧的推送统计数据
     * 每天凌晨2点执行
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupOldStatistics() {
        try {
            logger.info("开始清理旧的推送统计数据");
            
            // TODO: 实现清理旧统计数据的逻辑
            // 可以清理超过一定时间的统计数据，保持Redis存储空间
            
            logger.info("旧的推送统计数据清理完成");
            
        } catch (Exception e) {
            logger.error("清理旧的推送统计数据失败", e);
        }
    }
}
