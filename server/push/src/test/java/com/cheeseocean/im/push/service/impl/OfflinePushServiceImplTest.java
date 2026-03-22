package com.cheeseocean.im.push.service.impl;

import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.push.entity.OfflinePushConfig;
import com.cheeseocean.im.push.entity.OfflinePushResult;
import com.cheeseocean.im.push.entity.PushMessage;
import com.cheeseocean.im.push.provider.PushProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OfflinePushServiceImpl 测试类
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OfflinePushServiceImplTest {

    @Mock
    private List<PushProvider> pushProviders;

    @Mock
    private DeviceTokenServiceImpl deviceTokenService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private PushProvider pushProvider;

    @InjectMocks
    private OfflinePushServiceImpl offlinePushService;

    private Message testMessage;
    private List<String> targetUsers;

    @BeforeEach
    void setUp() {
        // 设置测试消息
        testMessage = new Message();
        testMessage.setServerMsgID("test-msg-123");
        testMessage.setSendID("sender-123");
        testMessage.setRecvID("receiver-123");
        testMessage.setContent("Test message content");
        testMessage.setContentType(101); // 文本消息
        testMessage.setSessionType(1); // 单聊
        testMessage.setSenderNickname("Test Sender");

        // 设置目标用户
        targetUsers = Arrays.asList("user1", "user2");

        // Mock Redis operations
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(pushProviders.isEmpty()).thenReturn(false);
        when(pushProviders.iterator()).thenReturn(List.of(pushProvider).iterator());
    }

    @Test
    void testPushMessageToUsers_Success() {
        // 准备测试数据
        Map<Integer, String> deviceTokens = new HashMap<>();
        deviceTokens.put(1, "ios-token-1");
        when(deviceTokenService.getUserDeviceTokens("user1")).thenReturn(deviceTokens);

        // Mock 推送提供商
        when(pushProvider.supportsPlatform(1)).thenReturn(true);
        when(pushProvider.isAvailable()).thenReturn(true);
        when(pushProvider.getProviderName()).thenReturn("TestProvider");
        when(pushProvider.sendPush(any(PushMessage.class)))
                .thenReturn(PushProvider.PushResult.success("msg-123"));

        // Mock Redis 配置
        Map<Object, Object> configMap = new HashMap<>();
        configMap.put("userID", "user1");
        configMap.put("enabled", "true");
        configMap.put("maxDailyCount", "100");
        configMap.put("currentDailyCount", "0");
        configMap.put("quietStartTime", "22:00");
        configMap.put("quietEndTime", "08:00");
        configMap.put("allowDuringQuietTime", "false");
        configMap.put("lastUpdateTime", String.valueOf(System.currentTimeMillis()));

        when(hashOperations.entries(anyString())).thenReturn(configMap);

        // 执行测试
        OfflinePushResult result = offlinePushService.pushMessageToUsers(testMessage, Arrays.asList("user1"));

        // 验证结果
        assertNotNull(result);
        // 注意：由于异步执行，这里可能需要等待或使用同步方式测试
    }

    @Test
    void pushMessageToUsersShouldBuildPushMessageFromDeviceTokensWithoutTemplateServiceStub() {
        Map<Integer, String> deviceTokens = new HashMap<>();
        deviceTokens.put(1, "ios-token-1");
        when(deviceTokenService.getUserDeviceTokens("user1")).thenReturn(deviceTokens);

        when(pushProvider.supportsPlatform(1)).thenReturn(true);
        when(pushProvider.isAvailable()).thenReturn(true);
        when(pushProvider.getProviderName()).thenReturn("TestProvider");
        when(pushProvider.sendPush(any(PushMessage.class)))
                .thenReturn(PushProvider.PushResult.success("provider-msg-1"));

        Map<Object, Object> configMap = new HashMap<>();
        configMap.put("userID", "user1");
        configMap.put("enabled", "true");
        configMap.put("maxDailyCount", "100");
        configMap.put("currentDailyCount", "0");
        when(hashOperations.entries(anyString())).thenReturn(configMap);

        OfflinePushResult result = offlinePushService.pushMessageToUsers(testMessage, List.of("user1"));

        assertNotNull(result);
        verify(pushProvider).sendPush(argThat(message ->
                "ios-token-1".equals(message.getDeviceToken())
                        && "user1".equals(message.getUserID())
                        && "Test Sender".equals(message.getTitle())
                        && "Test message content".equals(message.getContent())
                        && "test-msg-123".equals(message.getMessageID())));
    }

    @Test
    void testPushMessageToUser_Success() {
        // 准备测试数据
        String userID = "test-user";

        // Mock 配置
        Map<Object, Object> configMap = new HashMap<>();
        configMap.put("userID", userID);
        configMap.put("enabled", "true");
        when(hashOperations.entries(anyString())).thenReturn(configMap);

        // 执行测试
        OfflinePushResult result = offlinePushService.pushMessageToUser(testMessage, userID);

        // 验证结果
        assertNotNull(result);
    }

    @Test
    void testIsOfflinePushEnabled_True() {
        // Mock 配置
        Map<Object, Object> configMap = new HashMap<>();
        configMap.put("userID", "user1");
        configMap.put("enabled", "true");
        when(hashOperations.entries(anyString())).thenReturn(configMap);

        // 执行测试
        boolean result = offlinePushService.isOfflinePushEnabled("user1");

        // 验证结果
        assertTrue(result);
    }

    @Test
    void testIsOfflinePushEnabled_False() {
        // Mock 配置
        Map<Object, Object> configMap = new HashMap<>();
        configMap.put("userID", "user1");
        configMap.put("enabled", "false");
        when(hashOperations.entries(anyString())).thenReturn(configMap);

        // 执行测试
        boolean result = offlinePushService.isOfflinePushEnabled("user1");

        // 验证结果
        assertFalse(result);
    }

    @Test
    void testGetUserOfflinePushConfig_FromCache() {
        // Mock 配置
        Map<Object, Object> configMap = new HashMap<>();
        configMap.put("userID", "user1");
        configMap.put("enabled", "true");
        configMap.put("maxDailyCount", "100");
        configMap.put("currentDailyCount", "5");
        when(hashOperations.entries(anyString())).thenReturn(configMap);

        // 执行测试
        OfflinePushConfig config = offlinePushService.getUserOfflinePushConfig("user1");

        // 验证结果
        assertNotNull(config);
        assertEquals("user1", config.getUserID());
        assertTrue(config.isEnabled());
        assertEquals(100, config.getMaxDailyCount());
        assertEquals(5, config.getCurrentDailyCount());
    }

    @Test
    void testGetUserOfflinePushConfig_CreateDefault() {
        // Mock 空配置（缓存未命中）
        when(hashOperations.entries(anyString())).thenReturn(new HashMap<>());

        // 执行测试
        OfflinePushConfig config = offlinePushService.getUserOfflinePushConfig("user1");

        // 验证结果
        assertNotNull(config);
        assertEquals("user1", config.getUserID());
        assertTrue(config.isEnabled()); // 默认启用
        assertEquals(100, config.getMaxDailyCount()); // 默认值
        assertEquals(0, config.getCurrentDailyCount()); // 默认值
    }

    @Test
    void testUpdateUserOfflinePushConfig_Success() {
        // 准备测试数据
        OfflinePushConfig config = new OfflinePushConfig("user1");
        config.setEnabled(false);
        config.setMaxDailyCount(50);

        // 执行测试
        boolean result = offlinePushService.updateUserOfflinePushConfig("user1", config);

        // 验证结果
        assertTrue(result);
        verify(hashOperations).putAll(anyString(), any(Map.class));
        verify(redisTemplate).expire(anyString(), anyLong(), any());
    }
}
