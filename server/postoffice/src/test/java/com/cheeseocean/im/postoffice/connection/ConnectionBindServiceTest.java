package com.cheeseocean.im.postoffice.connection;

import com.cheeseocean.im.common.core.auth.SessionPrincipal;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConnectionBindServiceTest {

    @Test
    void bindAuthenticatedShouldPopulatePlatformIdFromSessionPlatform() {
        ConnectionManager connectionManager = mock(ConnectionManager.class);
        when(connectionManager.addConnection(org.mockito.ArgumentMatchers.any(UserConnection.class))).thenReturn(true);

        ConnectionBindService service = new ConnectionBindService(connectionManager);

        UserConnection connection = new UserConnection();
        connection.setConnectionID("conn-1");

        SessionPrincipal session = new SessionPrincipal();
        session.setUserId("userA");
        session.setSessionId("sess-1");
        session.setDeviceId("android-2");
        session.setPlatform("android");
        session.setTokenVersion(1L);

        boolean result = service.bindAuthenticated(connection, session);

        assertTrue(result);
        ArgumentCaptor<UserConnection> captor = ArgumentCaptor.forClass(UserConnection.class);
        verify(connectionManager).addConnection(captor.capture());
        UserConnection bound = captor.getValue();
        assertEquals(2, bound.getPlatformID());
        assertEquals(2, bound.getContext().getPlatformId());
    }
}
