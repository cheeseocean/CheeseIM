package com.cheeseocean.im.postoffice.connection;

import com.cheeseocean.im.common.api.enums.ConnectionState;
import com.cheeseocean.im.common.api.enums.PlatformType;
import com.cheeseocean.im.common.api.protocol.ClientEnvelope;
import com.cheeseocean.im.common.api.protocol.proto.ProtoAuthRequest;
import com.cheeseocean.im.common.api.session.ConnectionAuthService;
import com.cheeseocean.im.common.api.session.SessionPrincipal;
import com.cheeseocean.im.postoffice.config.NodeIdentityProvider;
import com.cheeseocean.im.postoffice.config.ServerProperties;
import com.cheeseocean.im.postoffice.handler.AuthMessageHandler;
import com.cheeseocean.im.postoffice.kickoff.NodeCommandPublisher;
import com.cheeseocean.im.postoffice.login.LoginLeaseClaim;
import com.cheeseocean.im.postoffice.login.LoginLeaseStore;
import com.cheeseocean.im.postoffice.service.OnlineRouteService;
import io.netty.channel.Channel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ConnectionBindServiceTest {
    private final OnlineRouteService routes = mock(OnlineRouteService.class);
    private final LoginLeaseStore leases = mock(LoginLeaseStore.class);
    private final NodeCommandPublisher publisher = mock(NodeCommandPublisher.class);
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private ConnectionManager manager;
    private ConnectionBindService binder;

    @AfterEach
    void cleanup() {
        executor.shutdownNow();
        if (manager != null) manager.destroy();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void lateAuthAfterDisconnectMustNotReviveOrKickValidConnection(boolean enforceLease) throws Exception {
        setup(enforceLease);
        UserConnection valid = pending("valid");
        assertTrue(binder.bindAuthenticated(valid, session("valid-session")));
        UserConnection stale = pending("stale");
        ConnectionAuthService auth = mock(ConnectionAuthService.class);
        CountDownLatch rpcEntered = new CountDownLatch(1);
        CountDownLatch returnRpc = new CountDownLatch(1);
        when(auth.authenticateWsTicket("ticket")).thenAnswer(invocation -> {
            rpcEntered.countDown();
            assertTrue(returnRpc.await(5, TimeUnit.SECONDS));
            return session("stale-session");
        });
        AuthMessageHandler handler = new AuthMessageHandler(binder);
        ReflectionTestUtils.setField(handler, "connectionAuthService", auth);
        clearInvocations(routes, leases, publisher, valid.getChannel());
        var result = executor.submit(() -> handler.handle(stale, authEnvelope()));
        try {
            assertTrue(rpcEntered.await(5, TimeUnit.SECONDS));
            when(stale.getChannel().isActive()).thenReturn(false);
            assertTrue(manager.removeConnectionByChannel(stale.getChannel()));
        } finally {
            returnRpc.countDown();
        }
        assertFalse(result.get(5, TimeUnit.SECONDS).isSuccess());
        assertFalse(stale.isAuthenticated());
        assertNull(stale.getUserID());
        assertEquals(ConnectionState.CLOSED, stale.getContext().getState());
        assertNull(manager.getConnection("stale"));
        assertNull(manager.getConnectionByChannel(stale.getChannel()));
        assertTrue(manager.getSessionConnections("stale-session").isEmpty());
        assertEquals(List.of(valid), manager.getUserConnections("userA"));
        assertSame(valid, manager.getConnection("valid"));
        assertTrue(valid.isAuthenticated());
        assertEquals(1, manager.getTotalConnectionCount());
        assertEquals(1, manager.getOnlineUserCount());
        assertFalse(manager.removeConnection("stale"));
        verifyNoInteractions(routes, leases, publisher);
        verify(valid.getChannel(), never()).close();
        verify(valid.getChannel(), never()).writeAndFlush(any());
    }

    @Test
    void pendingMustStillExistAsSameInstanceWhenPromotionAcquiresRealLock() throws Exception {
        setup(true);
        UserConnection stale = pending("reused-id");
        ReentrantLock[] locks = (ReentrantLock[]) ReflectionTestUtils.getField(manager, "connectionLocks");
        ReentrantLock lock = locks[Math.floorMod(stale.getConnectionID().hashCode(), locks.length)];
        CountDownLatch started = new CountDownLatch(1);
        lock.lock();
        java.util.concurrent.Future<Boolean> binding;
        UserConnection replacement;
        try {
            binding = executor.submit(() -> {
                started.countDown();
                return binder.bindAuthenticated(stale, session("stale-session"));
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertTrue(manager.removeConnection("reused-id"));
            replacement = pending("reused-id");
        } finally {
            lock.unlock();
        }
        assertFalse(binding.get(5, TimeUnit.SECONDS));
        assertSame(replacement, manager.getConnection("reused-id"));
        assertNull(stale.getUserID());
        assertFalse(stale.isAuthenticated());
        assertEquals(1, manager.getTotalConnectionCount());
        verifyNoInteractions(routes, leases, publisher);
    }

    @ParameterizedTest
    @ValueSource(strings = {"inactive", "closing", "closed", "disconnected"})
    void nonPromotablePendingMustNotHaveAuthenticationSideEffects(String state) {
        setup(true);
        UserConnection connection = pending("conn-1");
        switch (state) {
            case "inactive" -> when(connection.getChannel().isActive()).thenReturn(false);
            case "closing" -> connection.getContext().setState(ConnectionState.CLOSING);
            case "closed" -> connection.getContext().setState(ConnectionState.CLOSED);
            case "disconnected" -> connection.setStatus(UserConnection.STATUS_DISCONNECTED);
            default -> fail("unknown test state");
        }
        assertFalse(binder.bindAuthenticated(connection, session("session-1")));
        assertNull(connection.getUserID());
        assertFalse(connection.isAuthenticated());
        assertEquals(1, manager.getTotalConnectionCount());
        assertTrue(manager.removeConnection("conn-1"));
        assertFalse(manager.removeConnection("conn-1"));
        assertEquals(0, manager.getTotalConnectionCount());
        verifyNoInteractions(routes, leases, publisher);
    }

    @Test
    void promotionBeforeRemovalMustCleanEveryIndexAndReleaseExactlyOnce() throws Exception {
        setup(true);
        UserConnection connection = pending("conn-1");
        CountDownLatch routeEntered = new CountDownLatch(1);
        CountDownLatch finishRoute = new CountDownLatch(1);
        doAnswer(invocation -> {
            routeEntered.countDown();
            assertTrue(finishRoute.await(5, TimeUnit.SECONDS));
            return null;
        }).when(routes).register(any());
        var binding = executor.submit(() -> binder.bindAuthenticated(connection, session("session-1")));
        CountDownLatch removalStarted = new CountDownLatch(1);
        java.util.concurrent.Future<Boolean> removal;
        try {
            assertTrue(routeEntered.await(5, TimeUnit.SECONDS));
            removal = executor.submit(() -> {
                removalStarted.countDown();
                return manager.removeConnection("conn-1");
            });
            assertTrue(removalStarted.await(5, TimeUnit.SECONDS));
            assertFalse(removal.isDone());
        } finally {
            finishRoute.countDown();
        }
        assertTrue(binding.get(5, TimeUnit.SECONDS));
        assertTrue(removal.get(5, TimeUnit.SECONDS));
        assertFalse(manager.removeConnection("conn-1"));
        assertFalse(binder.bindAuthenticated(connection, session("session-1")));
        assertFalse(connection.isAuthenticated());
        assertEquals(ConnectionState.CLOSED, connection.getContext().getState());
        assertNull(manager.getConnectionByChannel(connection.getChannel()));
        assertTrue(manager.getUserConnections("userA").isEmpty());
        assertTrue(manager.getSessionConnections("session-1").isEmpty());
        assertTrue(manager.getDeviceConnections("userA", "android-2").isEmpty());
        assertEquals(0, manager.getTotalConnectionCount());
        assertEquals(0, manager.getOnlineUserCount());
        verify(leases, times(1)).claim(any(), any(), anyInt());
        verify(leases, times(1)).release(any());
        verify(routes, times(1)).register(any());
        verify(routes, times(1)).unregister("userA", "android-2", "conn-1");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void duplicateAuthForSameIdentityMustSucceedWithoutReclaimOrSelfKick(boolean enforceLease) {
        setup(enforceLease);
        UserConnection connection = pending("conn-1");
        ConnectionAuthService auth = mock(ConnectionAuthService.class);
        when(auth.authenticateWsTicket("ticket")).thenAnswer(invocation -> session("session-1"));
        AuthMessageHandler handler = new AuthMessageHandler(binder);
        ReflectionTestUtils.setField(handler, "connectionAuthService", auth);
        assertTrue(handler.handle(connection, authEnvelope()).isSuccess());
        assertEquals(PlatformType.ANDROID, connection.getPlatformType());
        assertEquals(PlatformType.ANDROID, connection.getContext().getPlatformCode());
        assertTrue(connection.getContext().getSessionValidatedAt() > 0);
        clearInvocations(routes, leases, publisher, connection.getChannel());
        assertTrue(handler.handle(connection, authEnvelope()).isSuccess());
        assertEquals(1, manager.getTotalConnectionCount());
        assertEquals(1, manager.getOnlineUserCount());
        verifyNoInteractions(routes, leases, publisher);
        verify(connection.getChannel(), never()).close();
        verify(connection.getChannel(), never()).writeAndFlush(any());
        // 连接身份不能被另一个 session 原地覆盖，否则旧 session/device 索引无法安全清理。
        assertFalse(binder.bindAuthenticated(connection, session("other-session")));
        assertEquals("session-1", connection.getSessionId());
        assertEquals(List.of(connection), manager.getSessionConnections("session-1"));
        assertTrue(manager.getSessionConnections("other-session").isEmpty());
    }

    @Test
    void routeRegistrationFailureMustReleaseSlotAndLeaseOnlyOnce() {
        setup(true);
        UserConnection connection = pending("conn-1");
        doThrow(new IllegalStateException("route unavailable")).when(routes).register(any());
        assertFalse(binder.bindAuthenticated(connection, session("session-1")));
        assertFalse(connection.isAuthenticated());
        assertEquals(ConnectionState.CLOSED, connection.getContext().getState());
        assertNull(manager.getConnection("conn-1"));
        assertFalse(manager.removeConnection("conn-1"));
        assertEquals(0, manager.getTotalConnectionCount());
        assertEquals(0, manager.getOnlineUserCount());
        assertTrue(manager.getSessionConnections("session-1").isEmpty());
        verify(leases, times(1)).release(any());
    }

    private void setup(boolean enforceLease) {
        ServerProperties properties = new ServerProperties();
        properties.getLoginLease().setEnforce(enforceLease);
        when(leases.claim(any(), any(), anyInt())).thenReturn(
                new LoginLeaseClaim(LoginLeaseClaim.Status.ACCEPTED, 1, List.of()));
        manager = new ConnectionManager(provider(routes), provider(null), new NodeIdentityProvider("test-node"),
                properties, provider(leases), provider(publisher));
        binder = new ConnectionBindService(manager);
    }

    private UserConnection pending(String id) {
        Channel channel = mock(Channel.class);
        when(channel.isActive()).thenReturn(true);
        UserConnection connection = new UserConnection();
        connection.setConnectionID(id);
        connection.setChannel(channel);
        connection.setContext(new ConnectionContext());
        assertTrue(manager.registerPendingConnection(connection));
        return connection;
    }

    private static SessionPrincipal session(String id) {
        SessionPrincipal session = new SessionPrincipal();
        session.setUserId("userA");
        session.setSessionId(id);
        session.setDeviceId("android-2");
        session.setPlatform("android");
        session.setTokenVersion(1L);
        return session;
    }

    private static ClientEnvelope authEnvelope() {
        ClientEnvelope envelope = new ClientEnvelope();
        envelope.setRequestId("auth-1");
        envelope.setBody(ProtoAuthRequest.newBuilder().setTicket("ticket").build().toByteArray());
        return envelope;
    }

    private static <T> ObjectProvider<T> provider(T value) {
        @SuppressWarnings("unchecked") ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
