package com.cheeseocean.im.push.listener;

import com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageResp;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchResult;
import com.cheeseocean.im.common.api.dto.message.MessageOptions;
import com.cheeseocean.im.common.api.dto.message.SequencedMessage;
import com.cheeseocean.im.common.api.dto.route.RouteSnapshot;
import com.cheeseocean.im.common.api.event.DeliveryEvent;
import com.cheeseocean.im.common.api.event.OfflinePushEvent;
import com.cheeseocean.im.common.api.route.OnlineRouteQueryRpc;
import com.cheeseocean.im.common.api.rpc.OnlineDispatchRpc;
import com.cheeseocean.im.common.core.constants.MessageConstants;
import com.cheeseocean.im.common.core.constants.TopicNames;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DeliveryEventListenerTest {

    @Test
    void deliveryListenerShouldDispatchOnlineWhenRoutesExist() {
        OnlineRouteQueryRpc routeQueryRpc = mock(OnlineRouteQueryRpc.class);
        OnlineDispatchRpc onlineDispatchRpc = mock(OnlineDispatchRpc.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);

        when(routeQueryRpc.findByUser("userB")).thenReturn(List.of(route("userB", "ios-1")));
        DispatchMessageResp dispatchResp = new DispatchMessageResp();
        dispatchResp.setResults(List.of(new DispatchResult("conn-1", true, "OK", "delivered")));
        when(onlineDispatchRpc.dispatchMessage(any())).thenReturn(dispatchResp);

        DeliveryEventListener listener = new DeliveryEventListener(new ObjectMapper(), routeQueryRpc, onlineDispatchRpc, kafkaTemplate);
        listener.handle(event(true));

        var captor = org.mockito.ArgumentCaptor.forClass(com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageReq.class);
        verify(onlineDispatchRpc).dispatchMessage(captor.capture());
        assertEquals("userA", captor.getValue().getPayload().getExt().get("senderId"));
        assertEquals("userB", captor.getValue().getPayload().getExt().get("recvId"));
        verify(kafkaTemplate, never()).send(eq(TopicNames.OFFLINE_PUSH), eq("userB"), any(OfflinePushEvent.class));
    }

    @Test
    void deliveryListenerShouldQueueOfflinePushWhenUserIsOffline() {
        OnlineRouteQueryRpc routeQueryRpc = mock(OnlineRouteQueryRpc.class);
        OnlineDispatchRpc onlineDispatchRpc = mock(OnlineDispatchRpc.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);

        when(routeQueryRpc.findByUser("userB")).thenReturn(List.of());

        DeliveryEventListener listener = new DeliveryEventListener(new ObjectMapper(), routeQueryRpc, onlineDispatchRpc, kafkaTemplate);
        listener.handle(event(true));

        verify(onlineDispatchRpc, never()).dispatchMessage(any());
        verify(kafkaTemplate).send(eq(TopicNames.OFFLINE_PUSH), eq("userB"), any(OfflinePushEvent.class));
    }

    @Test
    void deliveryListenerShouldSkipOfflinePushWhenOptionIsDisabled() {
        OnlineRouteQueryRpc routeQueryRpc = mock(OnlineRouteQueryRpc.class);
        OnlineDispatchRpc onlineDispatchRpc = mock(OnlineDispatchRpc.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);

        when(routeQueryRpc.findByUser("userB")).thenReturn(List.of());

        DeliveryEventListener listener = new DeliveryEventListener(new ObjectMapper(), routeQueryRpc, onlineDispatchRpc, kafkaTemplate);
        listener.handle(event(false));

        verify(onlineDispatchRpc, never()).dispatchMessage(any());
        verify(kafkaTemplate, never()).send(eq(TopicNames.OFFLINE_PUSH), eq("userB"), any(OfflinePushEvent.class));
    }

    @Test
    void deliveryListenerShouldPropagateNotificationMetadataIntoOfflinePushEvent() {
        OnlineRouteQueryRpc routeQueryRpc = mock(OnlineRouteQueryRpc.class);
        OnlineDispatchRpc onlineDispatchRpc = mock(OnlineDispatchRpc.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);

        when(routeQueryRpc.findByUser("userB")).thenReturn(List.of());

        DeliveryEventListener listener = new DeliveryEventListener(new ObjectMapper(), routeQueryRpc, onlineDispatchRpc, kafkaTemplate);
        DeliveryEvent event = event(true);
        event.getMessage().getOptions().setNotification(true);
        event.getMessage().setSessionType(MessageConstants.SESSION_TYPE_NOTIFICATION);
        event.getMessage().setContentType(MessageConstants.CONTENT_TYPE_SYSTEM_NOTIFY);

        listener.handle(event);

        var captor = org.mockito.ArgumentCaptor.forClass(OfflinePushEvent.class);
        verify(kafkaTemplate).send(eq(TopicNames.OFFLINE_PUSH), eq("userB"), captor.capture());
        OfflinePushEvent offlinePushEvent = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(true, offlinePushEvent.isNotification());
        org.junit.jupiter.api.Assertions.assertEquals(MessageConstants.SESSION_TYPE_NOTIFICATION, offlinePushEvent.getSessionType());
        org.junit.jupiter.api.Assertions.assertEquals(MessageConstants.CONTENT_TYPE_SYSTEM_NOTIFY, offlinePushEvent.getContentType());
        org.junit.jupiter.api.Assertions.assertEquals("userA", offlinePushEvent.getSenderId());
    }

    private static DeliveryEvent event(boolean needOfflinePush) {
        MessageOptions options = new MessageOptions();
        options.setNeedOfflinePush(needOfflinePush);
        options.setNeedOnlinePush(true);

        SequencedMessage message = new SequencedMessage();
        message.setConversationId("single:userA:userB");
        message.setSeq(12L);
        message.setClientMsgId("client-1");
        message.setServerMsgId("server-1");
        message.setSenderId("userA");
        message.setRecvId("userB");
        message.setSessionType(MessageConstants.SESSION_TYPE_SINGLE);
        message.setContentType(MessageConstants.CONTENT_TYPE_TEXT);
        message.setContent("hello");
        message.setSendTime(System.currentTimeMillis());
        message.setOptions(options);

        DeliveryEvent event = new DeliveryEvent();
        event.setConversationId(message.getConversationId());
        event.setMessage(message);
        event.setTargetUserIds(List.of("userB"));
        return event;
    }

    private static RouteSnapshot route(String userId, String deviceId) {
        RouteSnapshot route = new RouteSnapshot();
        route.setUserId(userId);
        route.setDeviceId(deviceId);
        return route;
    }
}
