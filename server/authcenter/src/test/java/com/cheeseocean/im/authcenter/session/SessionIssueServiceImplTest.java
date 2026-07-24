package com.cheeseocean.im.authcenter.session;

import com.cheeseocean.im.authcenter.auth.AccessTokenPrincipal;
import com.cheeseocean.im.authcenter.auth.AccessTokenService;
import com.cheeseocean.im.authcenter.repository.SessionRepository;
import com.cheeseocean.im.authcenter.repository.UserSecurityRepository;
import com.cheeseocean.im.common.api.enums.SessionStatus;
import com.cheeseocean.im.common.api.session.SessionPrincipal;
import com.cheeseocean.im.common.api.dto.user.WsTicketPrincipal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionIssueServiceImplTest {

    @Test
    void issueWsTicketShouldReusePersistedSessionAndSaveOnlyTicket() {
        AccessTokenService accessTokenService = mock(AccessTokenService.class);
        SessionTicketService sessionTicketService = mock(SessionTicketService.class);
        SessionRepository sessionRepository = mock(SessionRepository.class);
        UserSecurityRepository userSecurityRepository = mock(UserSecurityRepository.class);

        AccessTokenPrincipal principal = new AccessTokenPrincipal();
        principal.setUserId("u1");
        principal.setDeviceId("d1");
        principal.setPlatform("android");
        principal.setTokenVersion(1L);
        principal.setSessionId("sess:u1:d1");
        when(accessTokenService.validate("token-1")).thenReturn(principal);
        when(accessTokenService.getTokenExpirationMs()).thenReturn(5000L);
        when(userSecurityRepository.matchesTokenVersion("u1", 1L)).thenReturn(true);

        SessionPrincipal session = new SessionPrincipal();
        session.setSessionId("sess:u1:d1");
        session.setUserId("u1");
        session.setDeviceId("d1");
        session.setStatus(SessionStatus.ACTIVE);
        session.setTokenVersion(1L);
        when(sessionRepository.findBySessionId("sess:u1:d1")).thenReturn(session);

        WsTicketPrincipal ticket = new WsTicketPrincipal();
        ticket.setTicket("ticket-1");
        ticket.setExpireAt(System.currentTimeMillis() + 1000L);
        when(sessionTicketService.buildTicket(session)).thenReturn(ticket);
        when(sessionTicketService.wsTicketTtlMs()).thenReturn(1000L);

        SessionIssueServiceImpl service = new SessionIssueServiceImpl(
                accessTokenService,
                sessionTicketService,
                sessionRepository,
                userSecurityRepository);

        WsTicketPrincipal result = service.issueWsTicket("token-1", "d1", "android", "1.0");

        assertThat(result).isSameAs(ticket);
        verify(sessionRepository, never()).save(session, 5000L);
        verify(sessionRepository).saveWsTicket(eq(ticket), eq(1000L));
    }

    @Test
    void issueWsTicketShouldRejectRevokedSessionFromAccessToken() {
        AccessTokenService accessTokenService = mock(AccessTokenService.class);
        SessionTicketService sessionTicketService = mock(SessionTicketService.class);
        SessionRepository sessionRepository = mock(SessionRepository.class);
        UserSecurityRepository userSecurityRepository = mock(UserSecurityRepository.class);

        AccessTokenPrincipal principal = new AccessTokenPrincipal();
        principal.setUserId("u1");
        principal.setSessionId("sess:u1:d1");
        principal.setTokenVersion(1L);
        when(accessTokenService.validate("token-1")).thenReturn(principal);
        when(userSecurityRepository.matchesTokenVersion("u1", 1L)).thenReturn(true);

        SessionPrincipal session = new SessionPrincipal();
        session.setSessionId("sess:u1:d1");
        session.setUserId("u1");
        session.setStatus(SessionStatus.REVOKED);
        when(sessionRepository.findBySessionId("sess:u1:d1")).thenReturn(session);

        SessionIssueServiceImpl service = new SessionIssueServiceImpl(
                accessTokenService,
                sessionTicketService,
                sessionRepository,
                userSecurityRepository);

        assertThatThrownBy(() -> service.issueWsTicket("token-1", "d1", "android", "1.0"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("session invalid");
    }

    @Test
    void consumeWsTicketShouldDelegateAtomicConsumeToRepository() {
        AccessTokenService accessTokenService = mock(AccessTokenService.class);
        SessionTicketService sessionTicketService = mock(SessionTicketService.class);
        SessionRepository sessionRepository = mock(SessionRepository.class);
        UserSecurityRepository userSecurityRepository = mock(UserSecurityRepository.class);
        WsTicketPrincipal ticket = new WsTicketPrincipal();
        ticket.setTicket("ticket-1");
        when(sessionRepository.consumeWsTicket("ticket-1")).thenReturn(ticket);

        SessionIssueServiceImpl service = new SessionIssueServiceImpl(
                accessTokenService,
                sessionTicketService,
                sessionRepository,
                userSecurityRepository);

        WsTicketPrincipal result = service.consumeWsTicket("ticket-1");

        assertThat(result).isSameAs(ticket);
        verify(sessionRepository).consumeWsTicket("ticket-1");
    }
}
