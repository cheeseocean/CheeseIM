package com.cheeseocean.im.postman.service;

import com.cheeseocean.im.common.api.dto.message.MessageOptions;
import com.cheeseocean.im.common.api.dto.push.OfflinePushReq;
import com.cheeseocean.im.common.api.dto.push.PushResult;
import com.cheeseocean.im.common.api.enums.ChatType;
import com.cheeseocean.im.common.api.event.OfflinePushEvent;
import com.cheeseocean.im.common.api.enums.ContentType;
import com.cheeseocean.im.common.api.enums.DeliveryState;
import com.cheeseocean.im.postman.entity.OfflinePushResult;
import com.cheeseocean.im.postman.entity.PushAttempt;
import com.cheeseocean.im.postman.service.impl.MessagePushServiceImpl;
import com.cheeseocean.im.postman.state.PushStateStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MessagePostmanServiceImplTest {

    @Test
    void shouldNotPushWhenAnotherDeviceAlreadyConfirmedReceipt() {
        OfflinePushService offlinePushService = mock(OfflinePushService.class);
        PushDecisionService decisionService = new PushDecisionService();
        MessagePushServiceImpl service = service(offlinePushService, decisionService);

        OfflinePushReq message = new OfflinePushReq();
        message.setServerMsgId("s-1");
        message.setUserId("userB");
        service.recordDeliveryState("s-1", "userB", DeliveryState.ONLINE_CONFIRMED);

        PushResult result = service.pushOffline(message);

        assertFalse(result.isSuccess());
        verifyNoInteractions(offlinePushService);
    }

    @Test
    void sameServerMsgIdShouldNotCreateDuplicatePushAttempt() {
        OfflinePushService offlinePushService = mock(OfflinePushService.class);
        when(offlinePushService.pushMessageToUser(any(), eq("userB"))).thenReturn(OfflinePushResult.success(java.util.List.of("userB")));

        MessagePushServiceImpl service = service(offlinePushService, new PushDecisionService());

        OfflinePushReq message = new OfflinePushReq();
        message.setServerMsgId("s-2");
        message.setUserId("userB");

        PushResult first = service.pushOffline(message);
        PushResult second = service.pushOffline(message);

        assertTrue(first.isSuccess());
        assertFalse(second.isSuccess());
        verify(offlinePushService).pushMessageToUser(any(), eq("userB"));
    }

    @Test
    void cancelPendingShouldMarkAttemptCancelled() {
        OfflinePushService offlinePushService = mock(OfflinePushService.class);
        when(offlinePushService.pushMessageToUser(any(), eq("userB"))).thenReturn(OfflinePushResult.success(java.util.List.of("userB")));

        MessagePushServiceImpl service = service(offlinePushService, new PushDecisionService());

        OfflinePushReq message = new OfflinePushReq();
        message.setServerMsgId("s-3");
        message.setUserId("userB");
        service.pushOffline(message);

        service.cancelPending("s-3", "userB");

        PushAttempt attempt = service.findAttempt("s-3", "userB").orElseThrow();
        assertTrue(attempt.isCancelled());
    }

    @Test
    void offlinePushEventShouldMapIntoExistingPushFlow() {
        OfflinePushService offlinePushService = mock(OfflinePushService.class);
        when(offlinePushService.pushMessageToUser(any(), eq("userB"))).thenReturn(OfflinePushResult.success(java.util.List.of("userB")));

        MessagePushServiceImpl service = service(offlinePushService, new PushDecisionService());

        OfflinePushEvent task = new OfflinePushEvent();
        task.setServerMsgId("s-4");
        task.setConversationId("single:userA:userB");
        task.setSeq(17L);
        task.setUserId("userB");
        task.setSenderId("system");
        task.setSessionType(ChatType.NOTIFICATION.getCode());
        task.setContentType(ContentType.SYSTEM_NOTIFY.getCode());
        task.setNotification(true);
        task.setContent("ping");

        PushResult result = service.pushOffline(task);

        assertTrue(result.isSuccess());
        var messageCaptor = forClass(com.cheeseocean.im.common.api.dto.message.Message.class);
        verify(offlinePushService).pushMessageToUser(messageCaptor.capture(), eq("userB"));
        MessageOptions options = messageCaptor.getValue().getOptions();
        assertTrue(options != null && Boolean.TRUE.equals(options.getNotification()));
        assertEquals(ChatType.NOTIFICATION, messageCaptor.getValue().getChatType());
        assertEquals(ContentType.SYSTEM_NOTIFY, messageCaptor.getValue().getContentType());
    }

    private static MessagePushServiceImpl service(OfflinePushService offlinePushService,
                                                  PushDecisionService decisionService) {
        return new MessagePushServiceImpl(offlinePushService, decisionService, new InMemoryPushStateStore());
    }

    private static final class InMemoryPushStateStore implements PushStateStore {
        private final java.util.Map<String, PushAttempt> attempts = new java.util.HashMap<>();
        private final java.util.Map<String, DeliveryState> states = new java.util.HashMap<>();

        @Override
        public synchronized PushClaim claimPush(String serverMsgId, String userId) {
            String key = key(serverMsgId, userId);
            DeliveryState state = states.getOrDefault(key, DeliveryState.INBOXED);
            if (state == DeliveryState.ONLINE_CONFIRMED || state == DeliveryState.READ) {
                return new PushClaim(null, state, false);
            }
            if (attempts.containsKey(key)) return new PushClaim(null, state, true);
            PushAttempt attempt = new PushAttempt(serverMsgId, userId);
            attempts.put(key, attempt);
            return new PushClaim(attempt, state, false);
        }

        @Override
        public void cancelAttempt(String serverMsgId, String userId) {
            attempts.computeIfAbsent(key(serverMsgId, userId), ignored -> new PushAttempt(serverMsgId, userId)).cancel();
        }

        @Override
        public void recordDeliveryState(String serverMsgId, String userId, DeliveryState state) {
            states.put(key(serverMsgId, userId), state);
        }

        @Override
        public java.util.Optional<PushAttempt> findAttempt(String serverMsgId, String userId) {
            return java.util.Optional.ofNullable(attempts.get(key(serverMsgId, userId)));
        }

        @Override
        public java.util.Optional<PushAttempt> findAnyAttempt(String serverMsgId) {
            return attempts.values().stream().filter(attempt -> attempt.getServerMsgId().equals(serverMsgId)).findFirst();
        }

        @Override
        public boolean claimDailyQuota(String userId, int maxDailyCount) {
            return true;
        }

        @Override
        public void releaseDailyQuota(String userId) {
        }

        @Override
        public int getDailyPushCount(String userId) {
            return 0;
        }

        private String key(String serverMsgId, String userId) {
            return serverMsgId + ":" + userId;
        }
    }
}
