package com.cheeseocean.im.postoffice.handler;

import com.cheeseocean.im.common.api.conversation.DeliveryStateService;
import com.cheeseocean.im.common.api.conversation.ReadStateService;
import com.cheeseocean.im.common.api.conversation.TypingStateService;
import com.cheeseocean.im.common.api.dto.conversation.DeliverySeqUpdate;
import com.cheeseocean.im.common.api.dto.conversation.ReadSeqUpdate;
import com.cheeseocean.im.common.api.dto.conversation.TypingSignal;
import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.dto.message.MessageMutationResult;
import com.cheeseocean.im.common.api.dto.message.SendMessageResp;
import com.cheeseocean.im.common.api.enums.ChatType;
import com.cheeseocean.im.common.api.enums.CommandType;
import com.cheeseocean.im.common.api.enums.ConnectionState;
import com.cheeseocean.im.common.api.enums.ContentType;
import com.cheeseocean.im.common.api.enums.PlatformType;
import com.cheeseocean.im.common.api.enums.TypingActionEnum;
import com.cheeseocean.im.common.api.message.MessageMutationService;
import com.cheeseocean.im.common.api.protocol.ClientEnvelope;
import com.cheeseocean.im.common.api.protocol.ProtoMessageMapper;
import com.cheeseocean.im.common.api.protocol.proto.ProtoChatDeliveryAckCommand;
import com.cheeseocean.im.common.api.protocol.proto.ProtoChatReadCommand;
import com.cheeseocean.im.common.api.protocol.proto.ProtoChatRevokeCommand;
import com.cheeseocean.im.common.api.protocol.proto.ProtoChatTypingCommand;
import com.cheeseocean.im.common.api.rpc.MessageSender;
import com.cheeseocean.im.common.api.session.SessionQueryService;
import com.cheeseocean.im.postoffice.auth.ConnectionSessionGuard;
import com.cheeseocean.im.postoffice.config.ServerProperties;
import com.cheeseocean.im.postoffice.connection.ConnectionContext;
import com.cheeseocean.im.postoffice.connection.UserConnection;
import com.cheeseocean.im.postoffice.login.LoginLeaseHeartbeatBuffer;
import com.cheeseocean.im.postoffice.service.RouteHeartbeatBuffer;
import org.apache.dubbo.rpc.RpcException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 使用真实租约守卫验证命令入口，业务 RPC 和基础设施均为 mock。 */
class CommandSessionValidationTest {
    private static final String CONVERSATION = "s:user-1:user-2";
    private final SessionQueryService sessions = mock(SessionQueryService.class);
    private final ConnectionSessionGuard guard = new ConnectionSessionGuard(new ServerProperties());
    private final MessageSender sender = mock(MessageSender.class);
    private final ReadStateService reads = mock(ReadStateService.class);
    private final MessageMutationService mutations = mock(MessageMutationService.class);
    private final TypingStateService typing = mock(TypingStateService.class);
    private final DeliveryStateService deliveries = mock(DeliveryStateService.class);
    private final UserConnection connection = connection();

    CommandSessionValidationTest() {
        ReflectionTestUtils.setField(guard, "sessionQueryDubboService", sessions);
        when(sessions.isSessionValid("session-1")).thenReturn(true);
        SendMessageResp sent = new SendMessageResp();
        sent.setAccepted(true);
        sent.setServerMsgId("server-1");
        when(sender.sendMessage(any())).thenReturn(sent);
        ReadSeqUpdate read = new ReadSeqUpdate();
        read.setConversationId(CONVERSATION);
        read.setReaderUserId("user-1");
        read.setReadSeq(8L);
        when(reads.acknowledge(anyString(), anyString(), anyLong())).thenReturn(read);
        MessageMutationResult mutation = new MessageMutationResult();
        mutation.setSuccess(true);
        mutation.setConversationId(CONVERSATION);
        mutation.setServerMsgId("server-1");
        mutation.setOperatorUserId("user-1");
        mutation.setTargetSenderId("user-1");
        when(mutations.revoke(anyString(), anyString(), anyString(), anyString())).thenReturn(mutation);
        TypingSignal signal = new TypingSignal();
        signal.setConversationId(CONVERSATION);
        signal.setSenderId("user-1");
        signal.setAction(TypingActionEnum.START);
        when(typing.publish(anyString(), anyString(), any(), anyInt())).thenReturn(signal);
        DeliverySeqUpdate delivered = new DeliverySeqUpdate();
        delivered.setConversationId(CONVERSATION);
        delivered.setRecipientUserId("user-1");
        delivered.setDeviceId("ios-1");
        delivered.setDeliveredSeq(8L);
        when(deliveries.acknowledge(anyString(), anyString(), anyString(), anyLong(), anyString()))
                .thenReturn(delivered);
    }

    @ParameterizedTest
    @EnumSource(value = CommandType.class, names = {"CHAT_SEND", "CHAT_READ", "CHAT_REVOKE", "CHAT_TYPING", "CHAT_DELIVERY"})
    void commandWithoutHeartbeatMustRevalidateExpiredLeaseAndReuseIt(CommandType command) {
        MessageHandler handler = handler(command);
        // 默认仍为 60 秒，55 秒内的连续业务流量不新增认证 RPC。
        connection.getContext().setSessionValidatedAt(System.currentTimeMillis() - 55_000);
        assertTrue(handler.handle(connection, envelope(command)).isSuccess());
        verifyNoInteractions(sessions);
        connection.getContext().setSessionValidatedAt(System.currentTimeMillis() - 61_000);
        assertTrue(handler.handle(connection, envelope(command)).isSuccess());
        long renewedAt = connection.getContext().getSessionValidatedAt();
        assertTrue(handler.handle(connection, envelope(command)).isSuccess());
        assertEquals(renewedAt, connection.getContext().getSessionValidatedAt());
        verify(sessions, times(1)).isSessionValid("session-1");
        assertEquals(0, connection.getHeartbeatCount().get());
        assertEquals(3, mockingDetails(businessService(command)).getInvocations().size());
    }

    @ParameterizedTest
    @EnumSource(value = CommandType.class, names = {"CHAT_SEND", "CHAT_READ", "CHAT_REVOKE", "CHAT_TYPING", "CHAT_DELIVERY"})
    void revokedSessionWithoutHeartbeatMustStopBeforeBusinessRpc(CommandType command) {
        when(sessions.isSessionValid("session-1")).thenReturn(false);
        connection.getContext().setSessionValidatedAt(0);
        MessageHandler.HandleResult result = handler(command).handle(connection, envelope(command));
        assertFalse(result.isSuccess());
        assertEquals(CommandType.ERROR, result.getResponseEnvelope().getCommand());
        assertEquals(0, connection.getContext().getSessionValidatedAt());
        verify(sessions).isSessionValid("session-1");
        verifyNoInteractions(sender, reads, mutations, typing, deliveries);
    }

    @ParameterizedTest
    @EnumSource(value = CommandType.class, names = {"CHAT_SEND", "CHAT_READ", "CHAT_REVOKE", "CHAT_TYPING", "CHAT_DELIVERY"})
    void validationRpcFailureMustNotRenewLeaseOrContinueBusiness(CommandType command) {
        when(sessions.isSessionValid("session-1")).thenThrow(new RpcException("authcenter unavailable"));
        connection.getContext().setSessionValidatedAt(0);
        MessageHandler handler = handler(command);
        assertFalse(handler.handle(connection, envelope(command)).isSuccess());
        assertFalse(handler.handle(connection, envelope(command)).isSuccess());
        assertEquals(0, connection.getContext().getSessionValidatedAt());
        verify(sessions, times(2)).isSessionValid("session-1");
        verifyNoInteractions(sender, reads, mutations, typing, deliveries);
    }

    @Test
    void differentCommandsShareSingleFlightAndOneRenewedLease() throws Exception {
        CountDownLatch rpcEntered = new CountDownLatch(1);
        CountDownLatch finishRpc = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicReference<Thread> waiter = new AtomicReference<>();
        when(sessions.isSessionValid("session-1")).thenAnswer(invocation -> {
            rpcEntered.countDown();
            assertTrue(finishRpc.await(5, TimeUnit.SECONDS));
            return true;
        });
        connection.getContext().setSessionValidatedAt(0);
        MessageHandler read = handler(CommandType.CHAT_READ);
        MessageHandler type = handler(CommandType.CHAT_TYPING);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> read.handle(connection, envelope(CommandType.CHAT_READ)));
            assertTrue(rpcEntered.await(5, TimeUnit.SECONDS));
            var second = executor.submit(() -> {
                waiter.set(Thread.currentThread());
                secondStarted.countDown();
                return type.handle(connection, envelope(CommandType.CHAT_TYPING));
            });
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
            awaitWaitingOnConnection(waiter.get());
            assertFalse(first.isDone());
            assertFalse(second.isDone());
            verifyNoInteractions(reads, typing);
            finishRpc.countDown();
            assertTrue(first.get(5, TimeUnit.SECONDS).isSuccess());
            assertTrue(second.get(5, TimeUnit.SECONDS).isSuccess());
            verify(sessions, times(1)).isSessionValid("session-1");
            verify(reads).acknowledge("user-1", CONVERSATION, 8L);
            verify(typing).publish("user-1", CONVERSATION, TypingActionEnum.START, 3);
        } finally {
            finishRpc.countDown();
            executor.shutdownNow();
        }
    }

    private void awaitWaitingOnConnection(Thread waiter) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        var threads = ManagementFactory.getThreadMXBean();
        while (System.nanoTime() < deadline) {
            var info = threads.getThreadInfo(waiter.getId());
            if (info != null && info.getThreadState() == Thread.State.BLOCKED && info.getLockInfo() != null
                    && info.getLockInfo().getIdentityHashCode() == System.identityHashCode(connection)) {
                return;
            }
            Thread.onSpinWait();
        }
        fail("第二条命令未进入同连接单飞等待，不能证明交错复核只发生一次");
    }

    @Test
    void heartbeatRetainsCountersAndBothBuffersWhileSharingCommandLease() {
        RouteHeartbeatBuffer routes = mock(RouteHeartbeatBuffer.class);
        LoginLeaseHeartbeatBuffer leases = mock(LoginLeaseHeartbeatBuffer.class);
        HeartbeatMessageHandler heartbeat = new HeartbeatMessageHandler(routes, guard, leases);
        connection.getContext().setSessionValidatedAt(0);
        assertTrue(handler(CommandType.CHAT_READ).handle(connection, envelope(CommandType.CHAT_READ)).isSuccess());
        ClientEnvelope envelope = new ClientEnvelope();
        envelope.setRequestId("heartbeat-1");
        assertTrue(heartbeat.handle(connection, envelope).isSuccess());
        assertEquals(1, connection.getHeartbeatCount().get());
        verify(routes).record(connection);
        verify(leases).record(connection);
        verify(sessions, times(1)).isSessionValid("session-1");
    }

    private MessageHandler handler(CommandType command) {
        return switch (command) {
            case CHAT_SEND -> inject(new ChatMessageHandler(guard, new ServerProperties()), "messageSender", sender);
            case CHAT_READ -> inject(new ChatReadMessageHandler(guard), "readStateService", reads);
            case CHAT_REVOKE -> inject(new ChatRevokeMessageHandler(guard), "messageMutationService", mutations);
            case CHAT_TYPING -> inject(new ChatTypingMessageHandler(guard), "typingStateService", typing);
            case CHAT_DELIVERY -> inject(new ChatDeliveryMessageHandler(guard), "deliveryStateService", deliveries);
            default -> throw new IllegalArgumentException("unexpected command");
        };
    }

    private Object businessService(CommandType command) {
        return switch (command) {
            case CHAT_SEND -> sender;
            case CHAT_READ -> reads;
            case CHAT_REVOKE -> mutations;
            case CHAT_TYPING -> typing;
            case CHAT_DELIVERY -> deliveries;
            default -> throw new IllegalArgumentException("unexpected command");
        };
    }

    private static <T extends MessageHandler> T inject(T handler, String field, Object service) {
        ReflectionTestUtils.setField(handler, field, service);
        return handler;
    }

    private static ClientEnvelope envelope(CommandType command) {
        ClientEnvelope envelope = new ClientEnvelope();
        envelope.setCommand(command);
        envelope.setRequestId("command-1");
        envelope.setBody(switch (command) {
            case CHAT_SEND -> {
                Message message = new Message();
                message.setClientMsgId("client-1");
                message.setReceiverId("user-2");
                message.setContent("hello".getBytes(StandardCharsets.UTF_8));
                message.setContentType(ContentType.TEXT);
                message.setChatType(ChatType.PRIVATE);
                yield ProtoMessageMapper.toProto(message).toByteArray();
            }
            case CHAT_READ -> ProtoChatReadCommand.newBuilder().setConversationId(CONVERSATION)
                    .setReadSeq(8).build().toByteArray();
            case CHAT_REVOKE -> ProtoChatRevokeCommand.newBuilder().setConversationId(CONVERSATION)
                    .setServerMsgId("server-1").setReason("误发").build().toByteArray();
            case CHAT_TYPING -> ProtoChatTypingCommand.newBuilder().setConversationId(CONVERSATION)
                    .setAction(TypingActionEnum.START.getCode()).setTtlSeconds(3).build().toByteArray();
            case CHAT_DELIVERY -> ProtoChatDeliveryAckCommand.newBuilder().setConversationId(CONVERSATION)
                    .setDeviceId("ios-1").setMaxDeliveredSeq(8).setOpId("op-1").build().toByteArray();
            default -> throw new IllegalArgumentException("unexpected command");
        });
        return envelope;
    }

    private static UserConnection connection() {
        UserConnection connection = new UserConnection();
        connection.setConnectionID("conn-1");
        connection.setUserID("user-1");
        connection.setDeviceId("ios-1");
        ConnectionContext context = new ConnectionContext();
        context.setSessionId("session-1");
        context.setPlatformCode(PlatformType.IOS);
        connection.setContext(context);
        connection.setAuthenticated("ticket");
        assertEquals(ConnectionState.AUTHENTICATED, context.getState());
        return connection;
    }
}
