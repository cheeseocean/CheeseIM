package com.cheeseocean.im.message.task;

import com.cheeseocean.im.message.service.MessageStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 消息清理定时任务
 * 
 * @author CheeseIM
 */
@Component
public class MessageCleanupTask {
    
    private static final Logger logger = LoggerFactory.getLogger(MessageCleanupTask.class);
    
    @Autowired
    private MessageStorageService messageStorageService;
    
    /**
     * 消息保留天数，默认30天
     */
    @Value("${cheese.im.message.retention-days:30}")
    private int retentionDays;
    
    /**
     * 每天凌晨2点执行消息清理
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupExpiredMessages() {
        try {
            logger.info("开始清理过期消息，保留天数: {}", retentionDays);
            
            // 计算过期时间
            long expireTime = System.currentTimeMillis() - (retentionDays * 24 * 60 * 60 * 1000L);
            
            // 清理过期消息
            messageStorageService.cleanExpiredMessages(expireTime);
            
            logger.info("过期消息清理完成");
            
        } catch (Exception e) {
            logger.error("清理过期消息失败", e);
        }
    }
}
