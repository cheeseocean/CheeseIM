package com.cheeseocean.im.postman.listener;

import com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageReq;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageResp;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchResult;
import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.dto.message.MessageOptions;
import com.cheeseocean.im.common.api.dto.route.RouteSnapshot;
import com.cheeseocean.im.common.api.enums.ContentType;
import com.cheeseocean.im.common.api.enums.ChatType;
import com.cheeseocean.im.common.api.event.DeliveryEvent;
import com.cheeseocean.im.common.api.event.OfflinePushEvent;
import com.cheeseocean.im.common.api.protocol.ProtoOfflinePushEventMapper;
import com.cheeseocean.im.common.api.route.OnlineRouteQueryService;
import com.cheeseocean.im.common.api.rpc.OnlineDispatcher;
import com.cheeseocean.im.common.core.constants.TopicNames;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryEventListenerTest {

    @Test
    void deliveryListenerShouldDispatchOnlineWhenRoutesExist() {
        OnlineRouteQueryService routeQueryRpc = mock(OnlineRouteQueryService.class);
        OnlineDispatcher onlineDispatcher = mock(OnlineDispatcher.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);

        when(routeQueryRpc.findByUser("userB")).thenReturn(List.of(route("userB", "ios-1")));
        DispatchMessageResp dispatchResp = new DispatchMessageResp();
        dispatchResp.setResults(List.of(new DispatchResult("conn-1", true, "OK", "delivered")));
        when(onlineDispatcher.dispatchMessage(any())).thenReturn(dispatchResp);

        DeliveryEventListener listener = new DeliveryEventListener(routeQueryRpc, onlineDispatcher, kafkaTemplate);
        listener.handle(event(true));

        var captor = forClass(DispatchMessageReq.class);
        verify(onlineDispatcher).dispatchMessage(captor.capture());
        assertEquals("userA", captor.getValue().getPayload().getMsg().getSenderId());
        assertEquals("userB", captor.getValue().getPayload().getMsg().getReceiverId());
        verify(kafkaTemplate, never()).send(eq(TopicNames.OFFLINE_PUSH), eq("userB"), any(byte[].class));
    }

    @Test
    void deliveryListenerShouldQueueOfflinePushWhenUserIsOffline() {
        OnlineRouteQueryService routeQueryRpc = mock(OnlineRouteQueryService.class);
        OnlineDispatcher onlineDispatcher = mock(OnlineDispatcher.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);

        when(routeQueryRpc.findByUser("userB")).thenReturn(List.of());

        DeliveryEventListener listener = new DeliveryEventListener(routeQueryRpc, onlineDispatcher, kafkaTemplate);
        listener.handle(event(true));

        verify(onlineDispatcher, never()).dispatchMessage(any());
        verify(kafkaTemplate).send(eq(TopicNames.OFFLINE_PUSH), eq("userB"), any(byte[].class));
    }

    @Test
    void deliveryListenerShouldSkipOfflinePushWhenOptionIsDisabled() {
        OnlineRouteQueryService routeQueryRpc = mock(OnlineRouteQueryService.class);
        OnlineDispatcher onlineDispatcher = mock(OnlineDispatcher.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);

        when(routeQueryRpc.findByUser("userB")).thenReturn(List.of());

        DeliveryEventListener listener = new DeliveryEventListener(routeQueryRpc, onlineDispatcher, kafkaTemplate);
        listener.handle(event(false));

        verify(onlineDispatcher, never()).dispatchMessage(any());
        verify(kafkaTemplate, never()).send(eq(TopicNames.OFFLINE_PUSH), eq("userB"), any(byte[].class));
    }

    @Test
    void deliveryListenerShouldPropagateNotificationMetadataIntoOfflinePushEvent() throws Exception {
        OnlineRouteQueryService routeQueryRpc = mock(OnlineRouteQueryService.class);
        OnlineDispatcher onlineDispatcher = mock(OnlineDispatcher.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);

        when(routeQueryRpc.findByUser("userB")).thenReturn(List.of());

        DeliveryEventListener listener = new DeliveryEventListener(routeQueryRpc, onlineDispatcher, kafkaTemplate);
        DeliveryEvent event = event(true);
        event.getMessage().getOptions().setNotification(true);
        event.getMessage().setChatType(ChatType.NOTIFICATION);
        event.getMessage().setContentType(ContentType.SYSTEM_NOTIFY);

        listener.handle(event);

        var captor = forClass(byte[].class);
        verify(kafkaTemplate).send(eq(TopicNames.OFFLINE_PUSH), eq("userB"), captor.capture());
        OfflinePushEvent offlinePushEvent = ProtoOfflinePushEventMapper.parse(captor.getValue());
        assertEquals(true, offlinePushEvent.isNotification());
        assertEquals(ChatType.NOTIFICATION.getCode(), offlinePushEvent.getSessionType());
        assertEquals(ContentType.SYSTEM_NOTIFY.getCode(), offlinePushEvent.getContentType());
        assertEquals("userA", offlinePushEvent.getSenderId());
    }

    private static DeliveryEvent event(boolean needOfflinePush) {
        MessageOptions options = new MessageOptions();
        options.setNeedOfflinePush(needOfflinePush);
        options.setNeedOnlinePush(true);

        Message message = new Message();
        message.setSeq(12L);
        message.setClientMsgId("client-1");
        message.setServerMsgId("server-1");
        message.setSenderId("userA");
        message.setReceiverId("userB");
        message.setChatType(ChatType.PRIVATE);
        message.setContentType(ContentType.TEXT);
        message.setContent("hello".getBytes(StandardCharsets.UTF_8));
        message.setSendTime(System.currentTimeMillis());
        message.setOptions(options);

        DeliveryEvent event = new DeliveryEvent();
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
