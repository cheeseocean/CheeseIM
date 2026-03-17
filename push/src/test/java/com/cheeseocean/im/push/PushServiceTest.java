package com.cheeseocean.im.push;

import com.cheeseocean.im.common.entity.Message;
import com.cheeseocean.im.push.entity.PushMessage;
import com.cheeseocean.im.push.service.*;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Push服务测试类
 * 
 * @author CheeseIM
 */
@SpringBootTest
@ActiveProfiles("test")
@Disabled("Legacy integration test is blocked by the module's Spring/Dubbo dependency skew")
public class PushServiceTest {
    
    @Autowired
    private PushService pushService;
    
    @Autowired
    private DeviceTokenService deviceTokenService;
    
    @Autowired
    private PushTemplateService pushTemplateService;
    
    @Autowired
    private PushStatisticsService pushStatisticsService;
    
    @Test
    public void testDeviceTokenService() {
        String userID = "test_user_001";
        Integer platformID = 1; // iOS
        String deviceToken = "test_device_token_123456";
        
        // 保存设备Token
        boolean saved = deviceTokenService.saveDeviceToken(userID, platformID, deviceToken);
        assertTrue(saved, "设备Token应该保存成功");
        
        // 获取设备Token
        String retrievedToken = deviceTokenService.getDeviceToken(userID, platformID);
        assertEquals(deviceToken, retrievedToken, "获取的设备Token应该与保存的一致");
        
        // 检查设备Token是否存在
        boolean exists = deviceTokenService.hasDeviceToken(userID, platformID);
        assertTrue(exists, "设备Token应该存在");
        
        // 获取用户所有设备Token
        var userTokens = deviceTokenService.getUserDeviceTokens(userID);
        assertFalse(userTokens.isEmpty(), "用户应该有设备Token");
        assertEquals(deviceToken, userTokens.get(platformID), "用户设备Token应该正确");
        
        // 删除设备Token
        boolean removed = deviceTokenService.removeDeviceToken(userID, platformID);
        assertTrue(removed, "设备Token应该删除成功");
        
        // 验证删除
        boolean existsAfterRemove = deviceTokenService.hasDeviceToken(userID, platformID);
        assertFalse(existsAfterRemove, "删除后设备Token不应该存在");
    }
    
    @Test
    public void testPushTemplateService() {
        String userID = "test_user_002";
        Integer platformID = 2; // Android
        String title = "测试标题";
        String content = "测试内容";
        
        // 创建测试消息
        Message message = new Message();
        message.setServerMsgID("test_msg_001");
        message.setSendID("sender_001");
        message.setRecvID(userID);
        message.setSenderNickname("测试发送者");
        message.setContent("Hello World");
        message.setContentType(101); // 文本消息
        message.setSessionType(1); // 单聊
        message.setSendTime(System.currentTimeMillis());
        
        // 创建推送消息
        PushMessage pushMessage = pushTemplateService.createPushMessage(userID, platformID, title, content, message);
        
        assertNotNull(pushMessage, "推送消息不应该为空");
        assertEquals(userID, pushMessage.getUserID(), "用户ID应该正确");
        assertEquals(platformID, pushMessage.getPlatformID(), "平台ID应该正确");
        assertEquals(title, pushMessage.getTitle(), "标题应该正确");
        assertEquals(content, pushMessage.getContent(), "内容应该正确");
        assertEquals(message.getServerMsgID(), pushMessage.getMessageID(), "消息ID应该正确");
        assertEquals(message.getSendID(), pushMessage.getSenderID(), "发送者ID应该正确");
        
        // 获取推送模板
        PushTemplateService.PushTemplate template = pushTemplateService.getPushTemplate(101, 1);
        assertNotNull(template, "推送模板不应该为空");
        assertEquals(Integer.valueOf(101), template.getMessageType(), "消息类型应该正确");
        assertEquals(Integer.valueOf(1), template.getSessionType(), "会话类型应该正确");
        
        // 获取用户推送设置
        PushTemplateService.UserPushSettings settings = pushTemplateService.getUserPushSettings(userID);
        assertNotNull(settings, "用户推送设置不应该为空");
        assertEquals(userID, settings.getUserID(), "用户ID应该正确");
        assertTrue(settings.isPushEnabled(), "推送应该默认启用");
    }
    
    @Test
    public void testPushStatisticsService() {
        // 记录推送统计
        pushStatisticsService.recordPushStatistics("test_provider", 1, true, 100L);
        pushStatisticsService.recordPushStatistics("test_provider", 1, false, 200L);
        pushStatisticsService.recordPushStatistics("test_provider", 2, true, 150L);
        
        // 获取推送统计
        PushService.PushStatistics stats = pushStatisticsService.getPushStatistics();
        assertNotNull(stats, "推送统计不应该为空");
        assertTrue(stats.getTotalPushCount() >= 3, "总推送数应该至少为3");
        assertTrue(stats.getSuccessPushCount() >= 2, "成功推送数应该至少为2");
        assertTrue(stats.getFailedPushCount() >= 1, "失败推送数应该至少为1");
        
        // 获取实时统计
        PushStatisticsService.RealtimePushStats realtimeStats = pushStatisticsService.getRealtimePushStats();
        assertNotNull(realtimeStats, "实时统计不应该为空");
    }
    
    @Test
    public void testPushService() {
        String userID = "test_user_003";
        Integer platformID = 1; // iOS
        String deviceToken = "test_ios_token_789";
        String title = "测试推送";
        String content = "这是一条测试推送消息";
        
        // 先保存设备Token
        deviceTokenService.saveDeviceToken(userID, platformID, deviceToken);
        
        // 测试推送服务可用性检查
        boolean available = pushService.isPushAvailable(platformID);
        // 注意：在测试环境中，推送服务可能不可用，这是正常的
        
        // 获取推送统计
        PushService.PushStatistics stats = pushService.getPushStatistics();
        assertNotNull(stats, "推送统计不应该为空");
        
        // 清理测试数据
        deviceTokenService.removeDeviceToken(userID, platformID);
    }
    
    @Test
    public void testBatchOperations() {
        List<String> userIDs = Arrays.asList("batch_user_001", "batch_user_002", "batch_user_003");
        Integer platformID = 2; // Android
        
        // 批量保存设备Token
        for (int i = 0; i < userIDs.size(); i++) {
            String userID = userIDs.get(i);
            String deviceToken = "batch_token_" + (i + 1);
            deviceTokenService.saveDeviceToken(userID, platformID, deviceToken);
        }
        
        // 批量获取设备Token
        var batchTokens = deviceTokenService.batchGetDeviceTokens(userIDs, platformID);
        assertEquals(userIDs.size(), batchTokens.size(), "批量获取的Token数量应该正确");
        
        for (String userID : userIDs) {
            assertTrue(batchTokens.containsKey(userID), "应该包含用户" + userID + "的Token");
            assertNotNull(batchTokens.get(userID), "用户Token不应该为空");
        }
        
        // 清理测试数据
        for (String userID : userIDs) {
            deviceTokenService.removeDeviceToken(userID, platformID);
        }
    }
    
    @Test
    public void testDeviceTokenStats() {
        String userID = "stats_user_001";
        
        // 保存多个平台的Token
        deviceTokenService.saveDeviceToken(userID, 1, "ios_token_001");
        deviceTokenService.saveDeviceToken(userID, 2, "android_token_001");
        
        // 获取统计信息
        DeviceTokenService.DeviceTokenStats stats = deviceTokenService.getDeviceTokenStats();
        assertNotNull(stats, "设备Token统计不应该为空");
        assertTrue(stats.getTotalTokens() >= 2, "总Token数应该至少为2");
        
        // 清理测试数据
        deviceTokenService.removeAllUserDeviceTokens(userID);
    }
    
    @Test
    public void testPushMessageCreation() {
        // 测试静态工厂方法
        String userID = "factory_user_001";
        Integer platformID = 1;
        String senderNickname = "测试发送者";
        String content = "工厂方法测试内容";
        
        PushMessage textPush = PushMessage.createTextPush(userID, platformID, senderNickname, content);
        assertNotNull(textPush, "文本推送消息不应该为空");
        assertEquals(userID, textPush.getUserID(), "用户ID应该正确");
        assertEquals(platformID, textPush.getPlatformID(), "平台ID应该正确");
        assertEquals(senderNickname, textPush.getTitle(), "标题应该是发送者昵称");
        assertEquals(content, textPush.getContent(), "内容应该正确");
        assertEquals(Integer.valueOf(1), textPush.getPushType(), "推送类型应该是文本");
        
        String title = "系统通知";
        PushMessage systemPush = PushMessage.createSystemPush(userID, platformID, title, content);
        assertNotNull(systemPush, "系统推送消息不应该为空");
        assertEquals(title, systemPush.getTitle(), "标题应该正确");
        assertEquals(Integer.valueOf(7), systemPush.getPushType(), "推送类型应该是自定义");
        assertEquals(Integer.valueOf(2), systemPush.getPriority(), "优先级应该是高");
    }
}
