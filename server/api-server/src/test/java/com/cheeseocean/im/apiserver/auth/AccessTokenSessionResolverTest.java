package com.cheeseocean.im.apiserver.auth;

import com.cheeseocean.im.common.api.enums.SessionStatus;
import com.cheeseocean.im.common.api.session.SessionPrincipal;
import com.cheeseocean.im.common.api.session.SessionQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccessTokenSessionResolverTest {

    private SessionQueryService sessionQueryService;
    private AccessTokenSessionResolver resolver;

    @BeforeEach
    void setUp() {
        sessionQueryService = mock(SessionQueryService.class);
        resolver = new AccessTokenSessionResolver();
        ReflectionTestUtils.setField(resolver, "sessionQueryService", sessionQueryService);
    }

    @Test
    void resolveShouldReturnActiveSession() {
        SessionPrincipal principal = new SessionPrincipal();
        principal.setUserId("u100");
        principal.setStatus(SessionStatus.ACTIVE);
        when(sessionQueryService.getByAccessToken("token-1")).thenReturn(principal);

        SessionPrincipal actual = resolver.resolve("Bearer token-1");

        assertEquals("u100", actual.getUserId());
        verify(sessionQueryService).getByAccessToken("token-1");
    }

    @Test
    void resolveShouldRejectInvalidAuthorizationHeader() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> resolver.resolve("Basic token-1"));

        assertEquals("access token missing", ex.getMessage());
    }

    @Test
    void resolveShouldRejectInactiveSession() {
        SessionPrincipal principal = new SessionPrincipal();
        principal.setUserId("u100");
        principal.setStatus(SessionStatus.LOGOUT);
        when(sessionQueryService.getByAccessToken("token-1")).thenReturn(principal);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> resolver.resolve("Bearer token-1"));

        assertEquals("session invalid", ex.getMessage());
    }
}
