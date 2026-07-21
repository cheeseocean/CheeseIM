package com.cheeseocean.im.authcenter.session;

import com.cheeseocean.im.authcenter.repository.SessionRepository;
import com.cheeseocean.im.authcenter.repository.RefreshTokenRepository;
import com.cheeseocean.im.authcenter.repository.UserSecurityRepository;
import com.cheeseocean.im.authcenter.config.AuthCenterConfig;
import com.cheeseocean.im.common.api.connection.KickoffCommandService;
import com.cheeseocean.im.common.api.dto.user.KickoffCommand;
import com.cheeseocean.im.common.api.session.SessionPrincipal;
import com.cheeseocean.im.common.api.enums.SessionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionRevocationServiceImplTest {

    @Test
    void revokeSessionShouldMarkTheStoredSessionRevoked() {
        SessionRepository sessionRepository = mock(SessionRepository.class);
        UserSecurityRepository userSecurityRepository = mock(UserSecurityRepository.class);
        RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
        SessionPrincipal session = new SessionPrincipal();
        session.setSessionId("s1");
        session.setStatus(SessionStatus.ACTIVE);
        session.setRefreshTokenExpireAt(System.currentTimeMillis() + 60_000L);
        when(sessionRepository.findBySessionId("s1")).thenReturn(session);

        SessionRevocationServiceImpl service = new SessionRevocationServiceImpl(
                sessionRepository,
                userSecurityRepository,
                refreshTokenRepository,
                new AuthCenterConfig());
        KickoffCommandServiceStub kickoffCommandDubboService = new KickoffCommandServiceStub();
        ReflectionTestUtils.setField(service, "kickoffCommandService", kickoffCommandDubboService);

        service.revokeSession("s1", "logout");

        assertThat(session.getStatus()).isEqualTo(SessionStatus.REVOKED);
        verify(sessionRepository).updateSession(session);
        verify(refreshTokenRepository).revokeFamily(session.getRefreshTokenFamilyId());
        assertThat(kickoffCommandDubboService.lastCommand).isNotNull();
        assertThat(kickoffCommandDubboService.lastCommand.getSessionId()).isEqualTo("s1");
        assertThat(kickoffCommandDubboService.lastCommand.getReason()).isEqualTo("logout");
    }

    private static final class KickoffCommandServiceStub implements KickoffCommandService {
        private KickoffCommand lastCommand;

        @Override
        public void kickoffBySession(KickoffCommand command) {
            this.lastCommand = command;
        }

        @Override
        public void kickoffByUser(KickoffCommand command) {
            this.lastCommand = command;
        }

        @Override
        public void kickoffByDevice(KickoffCommand command) {
            this.lastCommand = command;
        }
    }
}
