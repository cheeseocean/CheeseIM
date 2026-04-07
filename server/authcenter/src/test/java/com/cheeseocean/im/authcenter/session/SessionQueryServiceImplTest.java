package com.cheeseocean.im.authcenter.session;

import com.cheeseocean.im.authcenter.auth.AccessTokenPrincipal;
import com.cheeseocean.im.authcenter.auth.AccessTokenService;
import com.cheeseocean.im.authcenter.repository.SessionRepository;
import com.cheeseocean.im.authcenter.repository.UserSecurityRepository;
import com.cheeseocean.im.common.api.session.SessionPrincipal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SessionQueryServiceImplTest {

    @Test
    void getBySessionIdShouldDelegateToSessionRepository() {
        SessionRepository sessionRepository = mock(SessionRepository.class);
        AccessTokenService accessTokenService = mock(AccessTokenService.class);
        SessionTicketService sessionTicketService = mock(SessionTicketService.class);
        UserSecurityRepository userSecurityRepository = mock(UserSecurityRepository.class);
        SessionPrincipal principal = new SessionPrincipal();
        principal.setSessionId("s1");
        when(sessionRepository.findBySessionId("s1")).thenReturn(principal);

        SessionQueryServiceImpl service = new SessionQueryServiceImpl(
                sessionRepository,
                accessTokenService,
                sessionTicketService,
                userSecurityRepository);

        assertThat(service.getBySessionId("s1")).isSameAs(principal);
    }

    @Test
    void getByAccessTokenShouldReturnCachedSessionFromRepository() {
        SessionRepository sessionRepository = mock(SessionRepository.class);
        AccessTokenService accessTokenService = mock(AccessTokenService.class);
        SessionTicketService sessionTicketService = mock(SessionTicketService.class);
        UserSecurityRepository userSecurityRepository = mock(UserSecurityRepository.class);

        AccessTokenPrincipal tokenPrincipal = new AccessTokenPrincipal();
        tokenPrincipal.setUserId("u1");
        tokenPrincipal.setDeviceId("d1");
        tokenPrincipal.setPlatform("android");
        when(accessTokenService.validate("token-1")).thenReturn(tokenPrincipal);

        SessionPrincipal cached = new SessionPrincipal();
        cached.setSessionId("sess:u1:d1");
        when(sessionRepository.findBySessionId("sess:u1:d1")).thenReturn(cached);

        SessionQueryServiceImpl service = new SessionQueryServiceImpl(
                sessionRepository,
                accessTokenService,
                sessionTicketService,
                userSecurityRepository);

        assertThat(service.getByAccessToken("token-1")).isSameAs(cached);
    }
}
