package com.cheeseocean.im.postman.listener;

import com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageReq;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageResp;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchResult;
import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.dto.message.MessageOptions;
import com.cheeseocean.im.common.api.dto.route.RouteSnapshot;
import com.cheeseocean.im.common.api.enums.ContentType;
import com.cheeseocean.im.common.api.enums.ChatType;
import com.cheeseocean.im.common.api.event.OfflinePushEvent;
import com.cheeseocean.im.common.api.protocol.ProtoOfflinePushEventMapper;
import com.cheeseocean.im.common.api.route.OnlineRouteQueryService;
import com.cheeseocean.im.common.api.rpc.OnlineDispatcher;
import com.cheeseocean.im.common.core.constants.TopicNames;
import com.cheeseocean.im.common.core.queue.QueueAdapter;
import com.cheeseocean.im.postman.sender.OfflinePushEventProducer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P0-6 修复后：{@link DeliveryEventListener} 通过 {@link OfflinePushEventProducer} → {@link QueueAdapter}
 * 投递 OFFLINE_PUSH，而不再直连 {@code KafkaTemplate}。本测试随之改为 mock {@link QueueAdapter}，
 * 验证 (a) 离线分支调用 {@code queueAdapter.send(OFFLINE_PUSH, userId, bytes)}；
 * (b) 在线投递成功时不进入离线分支； (c) notification 元数据透传到 {@link OfflinePushEvent}。
 */
class DeliveryEventListenerTest {

    @Test
    void deliveryListenerShouldDispatchOnlineWhenRoutesExist() {
        OnlineRouteQueryService routeQueryRpc = mock(OnlineRouteQueryService.class);
        OnlineDispatcher onlineDispatcher = mock(OnlineDispatcher.class);
        QueueAdapter queueAdapter = mock(QueueAdapter.class);

        when(routeQueryRpc.findByUser("userB")).thenReturn(List.of(route("userB", "ios-1")));
        DispatchMessageResp dispatchResp = new DispatchMessageResp();
        dispatchResp.setResults(List.of(new DispatchResult("conn-1", true, "OK", "delivered")));
        when(onlineDispatcher.dispatchMessage(any())).thenReturn(dispatchResp);

        DeliveryEventListener listener = new DeliveryEventListener(
                routeQueryRpc, onlineDispatcher, new OfflinePushEventProducer(queueAdapter));
        listener.handle(message(true));

        verify(onlineDispatcher).dispatchMessage(any(DispatchMessageReq.class));
        verify(queueAdapter, never()).send(eq(TopicNames.OFFLINE_PUSH), eq("userB"), any(byte[].class));
    }

    @Test
    void deliveryListenerShouldQueueOfflinePushWhenUserIsOffline() {
        OnlineRouteQueryService routeQueryRpc = mock(OnlineRouteQueryService.class);
        OnlineDispatcher onlineDispatcher = mock(OnlineDispatcher.class);
        QueueAdapter queueAdapter = mock(QueueAdapter.class);

        when(routeQueryRpc.findByUser("userB")).thenReturn(List.of());

        DeliveryEventListener listener = new DeliveryEventListener(
                routeQueryRpc, onlineDispatcher, new OfflinePushEventProducer(queueAdapter));
        listener.handle(message(true));

        verify(onlineDispatcher, never()).dispatchMessage(any());
        verify(queueAdapter).send(eq(TopicNames.OFFLINE_PUSH), eq("userB"), any(byte[].class));
    }

    @Test
    void deliveryListenerShouldSkipOfflinePushWhenOptionIsDisabled() {
        OnlineRouteQueryService routeQueryRpc = mock(OnlineRouteQueryService.class);
        OnlineDispatcher onlineDispatcher = mock(OnlineDispatcher.class);
        QueueAdapter queueAdapter = mock(QueueAdapter.class);

        when(routeQueryRpc.findByUser("userB")).thenReturn(List.of());

        DeliveryEventListener listener = new DeliveryEventListener(
                routeQueryRpc, onlineDispatcher, new OfflinePushEventProducer(queueAdapter));
        listener.handle(message(false));

        verify(onlineDispatcher, never()).dispatchMessage(any());
        verify(queueAdapter, never()).send(eq(TopicNames.OFFLINE_PUSH), eq("userB"), any(byte[].class));
    }

    @Test
    void deliveryListenerShouldPropagateNotificationMetadataIntoOfflinePushEvent() throws Exception {
        OnlineRouteQueryService routeQueryRpc = mock(OnlineRouteQueryService.class);
        OnlineDispatcher onlineDispatcher = mock(OnlineDispatcher.class);
        QueueAdapter queueAdapter = mock(QueueAdapter.class);

        when(routeQueryRpc.findByUser("userB")).thenReturn(List.of());

        DeliveryEventListener listener = new DeliveryEventListener(
                routeQueryRpc, onlineDispatcher, new OfflinePushEventProducer(queueAdapter));
        Message m = message(true);
        m.getOptions().setNotification(true);
        m.setChatType(ChatType.NOTIFICATION);
        m.setContentType(ContentType.SYSTEM_NOTIFY);

        listener.handle(m);

        ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
        verify(queueAdapter).send(eq(TopicNames.OFFLINE_PUSH), eq("userB"), captor.capture());
        OfflinePushEvent offlinePushEvent = ProtoOfflinePushEventMapper.parse(captor.getValue());
        assertEquals(true, offlinePushEvent.isNotification());
        assertEquals(ChatType.NOTIFICATION.getCode(), offlinePushEvent.getSessionType());
        assertEquals(ContentType.SYSTEM_NOTIFY.getCode(), offlinePushEvent.getContentType());
        assertEquals("userA", offlinePushEvent.getSenderId());
    }

    private static Message message(boolean needOfflinePush) {
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
        return message;
    }

    private static RouteSnapshot route(String userId, String deviceId) {
        RouteSnapshot route = new RouteSnapshot();
        route.setUserId(userId);
        route.setDeviceId(deviceId);
        return route;
    }
}