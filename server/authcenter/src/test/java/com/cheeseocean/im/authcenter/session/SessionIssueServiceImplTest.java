package com.cheeseocean.im.authcenter.session;

import com.cheeseocean.im.authcenter.auth.AccessTokenPrincipal;
import com.cheeseocean.im.authcenter.auth.AccessTokenService;
import com.cheeseocean.im.authcenter.repository.SessionRepository;
import com.cheeseocean.im.common.api.session.SessionPrincipal;
import com.cheeseocean.im.common.api.dto.user.WsTicketPrincipal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionIssueServiceImplTest {

    @Test
    void issueWsTicketShouldPersistSessionThroughSessionRepository() {
        AccessTokenService accessTokenService = mock(AccessTokenService.class);
        SessionTicketService sessionTicketService = mock(SessionTicketService.class);
        SessionRepository sessionRepository = mock(SessionRepository.class);

        AccessTokenPrincipal principal = new AccessTokenPrincipal();
        principal.setUserId("u1");
        principal.setDeviceId("d1");
        principal.setPlatform("android");
        when(accessTokenService.validate("token-1")).thenReturn(principal);
        when(accessTokenService.getTokenExpirationMs()).thenReturn(5000L);

        SessionPrincipal session = new SessionPrincipal();
        session.setSessionId("sess:u1:d1");
        session.setUserId("u1");
        session.setDeviceId("d1");
        when(sessionTicketService.buildSession(principal, "d1", "android", "1.0")).thenReturn(session);

        WsTicketPrincipal ticket = new WsTicketPrincipal();
        ticket.setTicket("ticket-1");
        ticket.setExpireAt(System.currentTimeMillis() + 1000L);
        when(sessionTicketService.buildTicket(session)).thenReturn(ticket);
        when(sessionTicketService.wsTicketTtlMs()).thenReturn(1000L);

        SessionIssueServiceImpl service = new SessionIssueServiceImpl(
                accessTokenService,
                sessionTicketService,
                sessionRepository);

        WsTicketPrincipal result = service.issueWsTicket("token-1", "d1", "android", "1.0");

        assertThat(result).isSameAs(ticket);
        verify(sessionRepository).save(session, 5000L);
        verify(sessionRepository).saveWsTicket(eq(ticket), eq(1000L));
    }
}
