package com.cheeseocean.im.authcenter.session;

import com.cheeseocean.im.common.api.connection.KickoffCommandService;
import com.cheeseocean.im.common.api.session.SessionRevocationService;
import com.cheeseocean.im.authcenter.repository.SessionRepository;
import com.cheeseocean.im.common.core.auth.KickoffCommand;
import com.cheeseocean.im.common.core.auth.SessionPrincipal;
import com.cheeseocean.im.common.core.enums.SessionStatus;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

@Service
@DubboService
public class SessionRevocationServiceImpl implements SessionRevocationService {

    private final SessionRepository sessionRepository;

    @DubboReference(check = false)
    private KickoffCommandService kickoffCommandService;

    public SessionRevocationServiceImpl(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Override
    public void revokeSession(String sessionId, String reason) {
        SessionPrincipal session = sessionRepository.findBySessionId(sessionId);
        if (session == null) {
            return;
        }
        session.setStatus(SessionStatus.REVOKED);
        sessionRepository.save(session);

        KickoffCommand command = new KickoffCommand();
        command.setSessionId(sessionId);
        command.setReason(reason);
        kickoffCommandService.kickoffBySession(command);
    }

    @Override
    public void revokeUserSessions(String userId, String reason) {
        for (SessionPrincipal session : sessionRepository.findByUserId(userId)) {
            revokeSession(session.getSessionId(), reason);
        }
    }

    @Override
    public void revokeDeviceSession(String userId, String deviceId, String reason) {
        SessionPrincipal session = sessionRepository.findByDevice(userId, deviceId);
        if (session == null) {
            return;
        }
        revokeSession(session.getSessionId(), reason);
    }
}
