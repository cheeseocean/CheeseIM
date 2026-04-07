package com.cheeseocean.im.authcenter.session;

import com.cheeseocean.im.authcenter.repository.SessionRepository;
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
        SessionPrincipal session = new SessionPrincipal();
        session.setSessionId("s1");
        session.setStatus(SessionStatus.ACTIVE);
        when(sessionRepository.findBySessionId("s1")).thenReturn(session);

        SessionRevocationServiceImpl service                    = new SessionRevocationServiceImpl(sessionRepository);
        KickoffCommandServiceStub    kickoffCommandDubboService = new KickoffCommandServiceStub();
        ReflectionTestUtils.setField(service, "kickoffCommandDubboService", kickoffCommandDubboService);

        service.revokeSession("s1", "logout");

        assertThat(session.getStatus()).isEqualTo(SessionStatus.REVOKED);
        verify(sessionRepository).save(session);
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
