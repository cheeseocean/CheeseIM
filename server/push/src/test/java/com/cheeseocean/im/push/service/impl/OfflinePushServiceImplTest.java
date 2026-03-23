package com.cheeseocean.im.push.service.impl;

import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.core.constants.MessageDisplayConstants;
import com.cheeseocean.im.common.core.enums.ContentType;
import com.cheeseocean.im.common.core.enums.PlatformType;
import com.cheeseocean.im.common.core.enums.SessionType;
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
import org.mockito.ArgumentCaptor;
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
        testMessage.setContentType(ContentType.TEXT.getCode());
        testMessage.setSessionType(SessionType.SINGLE.getCode());
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
        when(pushProvider.supportsPlatform(PlatformType.IOS)).thenReturn(true);
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

        when(pushProvider.supportsPlatform(PlatformType.IOS)).thenReturn(true);
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
    void pushMessageToUsersShouldUseSystemNotificationTitleForNotificationMessages() {
        Map<Integer, String> deviceTokens = new HashMap<>();
        deviceTokens.put(1, "ios-token-1");
        when(deviceTokenService.getUserDeviceTokens("user1")).thenReturn(deviceTokens);

        when(pushProvider.supportsPlatform(PlatformType.IOS)).thenReturn(true);
        when(pushProvider.isAvailable()).thenReturn(true);
        when(pushProvider.getProviderName()).thenReturn("TestProvider");
        when(pushProvider.sendPush(any(PushMessage.class)))
                .thenReturn(PushProvider.PushResult.success("provider-msg-2"));

        Map<Object, Object> configMap = new HashMap<>();
        configMap.put("userID", "user1");
        configMap.put("enabled", "true");
        when(hashOperations.entries(anyString())).thenReturn(configMap);

        Message notificationMessage = new Message();
        notificationMessage.setServerMsgID("notification-msg-1");
        notificationMessage.setSendID("system");
        notificationMessage.setRecvID("user1");
        notificationMessage.setContent("Your policy was updated");
        notificationMessage.setContentType(ContentType.SYSTEM_NOTIFY.getCode());
        notificationMessage.setSessionType(SessionType.NOTIFICATION.getCode());
        notificationMessage.setOptions(Map.of("notification", true));

        OfflinePushResult result = offlinePushService.pushMessageToUsers(notificationMessage, List.of("user1"));

        assertNotNull(result);
        verify(pushProvider).sendPush(argThat(message ->
                "系统通知".equals(message.getTitle())
                        && "Your policy was updated".equals(message.getContent())
                        && "notification-msg-1".equals(message.getMessageID())));
    }

    @Test
    void pushMessageToUsersShouldUseNotificationFallbackContentWhenNotificationBodyIsBlank() {
        Map<Integer, String> deviceTokens = new HashMap<>();
        deviceTokens.put(1, "ios-token-1");
        when(deviceTokenService.getUserDeviceTokens("user1")).thenReturn(deviceTokens);

        when(pushProvider.supportsPlatform(PlatformType.IOS)).thenReturn(true);
        when(pushProvider.isAvailable()).thenReturn(true);
        when(pushProvider.getProviderName()).thenReturn("TestProvider");
        when(pushProvider.sendPush(any(PushMessage.class)))
                .thenReturn(PushProvider.PushResult.success("provider-msg-3"));

        Map<Object, Object> configMap = new HashMap<>();
        configMap.put("userID", "user1");
        configMap.put("enabled", "true");
        when(hashOperations.entries(anyString())).thenReturn(configMap);

        Message notificationMessage = new Message();
        notificationMessage.setServerMsgID("notification-msg-2");
        notificationMessage.setSendID("system");
        notificationMessage.setRecvID("user1");
        notificationMessage.setContent("   ");
        notificationMessage.setContentType(ContentType.FORCE_LOGOUT.getCode());
        notificationMessage.setSessionType(SessionType.NOTIFICATION.getCode());
        notificationMessage.setOptions(Map.of("notification", true));

        OfflinePushResult result = offlinePushService.pushMessageToUsers(notificationMessage, List.of("user1"));

        assertNotNull(result);
        verify(pushProvider).sendPush(argThat(message ->
                MessageDisplayConstants.PUSH_TITLE_SYSTEM_NOTIFICATION.equals(message.getTitle())
                        && MessageDisplayConstants.PUSH_CONTENT_NEW_SYSTEM_NOTIFICATION.equals(message.getContent())));
    }

    @Test
    void pushMessageToUsersShouldUseSharedFallbackLabelsForNonTextContent() {
        Map<Integer, String> deviceTokens = new HashMap<>();
        deviceTokens.put(1, "ios-token-1");
        when(deviceTokenService.getUserDeviceTokens("user1")).thenReturn(deviceTokens);

        when(pushProvider.supportsPlatform(PlatformType.IOS)).thenReturn(true);
        when(pushProvider.isAvailable()).thenReturn(true);
        when(pushProvider.getProviderName()).thenReturn("TestProvider");
        when(pushProvider.sendPush(any(PushMessage.class)))
                .thenReturn(PushProvider.PushResult.success("provider-msg-4"));

        Map<Object, Object> configMap = new HashMap<>();
        configMap.put("userID", "user1");
        configMap.put("enabled", "true");
        when(hashOperations.entries(anyString())).thenReturn(configMap);

        Message imageMessage = new Message();
        imageMessage.setServerMsgID("image-msg-1");
        imageMessage.setSendID("userA");
        imageMessage.setRecvID("user1");
        imageMessage.setContent("");
        imageMessage.setContentType(ContentType.IMAGE.getCode());
        imageMessage.setSessionType(SessionType.GROUP.getCode());
        imageMessage.setSenderNickname(null);

        OfflinePushResult result = offlinePushService.pushMessageToUsers(imageMessage, List.of("user1"));

        assertNotNull(result);
        verify(pushProvider).sendPush(argThat(message ->
                MessageDisplayConstants.PUSH_TITLE_GROUP_MESSAGE.equals(message.getTitle())
                        && MessageDisplayConstants.PUSH_CONTENT_IMAGE.equals(message.getContent())));
    }

    @Test
    void pushMessageToUsersShouldFallbackToDirectTitleWhenSessionTypeIsMissing() {
        Map<Integer, String> deviceTokens = new HashMap<>();
        deviceTokens.put(1, "ios-token-1");
        when(deviceTokenService.getUserDeviceTokens("user1")).thenReturn(deviceTokens);

        when(pushProvider.supportsPlatform(PlatformType.IOS)).thenReturn(true);
        when(pushProvider.isAvailable()).thenReturn(true);
        when(pushProvider.getProviderName()).thenReturn("TestProvider");
        when(pushProvider.sendPush(any(PushMessage.class)))
                .thenReturn(PushProvider.PushResult.success("provider-msg-5"));

        Map<Object, Object> configMap = new HashMap<>();
        configMap.put("userID", "user1");
        configMap.put("enabled", "true");
        when(hashOperations.entries(anyString())).thenReturn(configMap);

        Message messageWithoutSessionType = new Message();
        messageWithoutSessionType.setServerMsgID("missing-session-type");
        messageWithoutSessionType.setSendID("userA");
        messageWithoutSessionType.setRecvID("user1");
        messageWithoutSessionType.setContent("hello");
        messageWithoutSessionType.setContentType(ContentType.TEXT.getCode());
        messageWithoutSessionType.setSenderNickname("userA");

        OfflinePushResult result = offlinePushService.pushMessageToUsers(messageWithoutSessionType, List.of("user1"));

        assertNotNull(result);
        ArgumentCaptor<PushMessage> messageCaptor = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushProvider).sendPush(messageCaptor.capture());
        assertEquals("userA", messageCaptor.getValue().getTitle());
        assertEquals("hello", messageCaptor.getValue().getContent());
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
