package com.cheeseocean.im.authcenter.session;

import com.cheeseocean.im.common.api.connection.KickoffCommandService;
import com.cheeseocean.im.common.api.session.SessionRevocationService;
import com.cheeseocean.im.authcenter.repository.RefreshTokenRepository;
import com.cheeseocean.im.authcenter.repository.SessionRepository;
import com.cheeseocean.im.authcenter.repository.UserSecurityRepository;
import com.cheeseocean.im.authcenter.config.AuthCenterConfig;
import com.cheeseocean.im.common.api.dto.user.KickoffCommand;
import com.cheeseocean.im.common.api.session.SessionPrincipal;
import com.cheeseocean.im.common.api.enums.SessionStatus;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

@Service
@DubboService
public class SessionRevocationServiceImpl implements SessionRevocationService {

    private final SessionRepository sessionRepository;
    private final UserSecurityRepository userSecurityRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthCenterConfig authCenterConfig;

    @DubboReference(check = false, retries = 0)
    private KickoffCommandService kickoffCommandService;

    public SessionRevocationServiceImpl(SessionRepository sessionRepository,
                                        UserSecurityRepository userSecurityRepository,
                                        RefreshTokenRepository refreshTokenRepository,
                                        AuthCenterConfig authCenterConfig) {
        this.sessionRepository = sessionRepository;
        this.userSecurityRepository = userSecurityRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.authCenterConfig = authCenterConfig;
    }

    @Override
    public void revokeSession(String sessionId, String reason) {
        SessionPrincipal session = sessionRepository.findBySessionId(sessionId);
        if (session == null) {
            return;
        }
        session.setStatus(SessionStatus.REVOKED);
        if (session.getRefreshTokenExpireAt() == null) {
            // 发布前创建的存量 session 没有 family 绝对期限，按 access token 最大存活期保留撤销态。
            sessionRepository.updateSession(session, authCenterConfig.getSecurity().getTokenExpiration());
        } else {
            sessionRepository.updateSession(session);
        }
        refreshTokenRepository.revokeFamily(session.getRefreshTokenFamilyId());

        KickoffCommand command = new KickoffCommand();
        command.setSessionId(sessionId);
        command.setReason(reason);
        kickoffCommandService.kickoffBySession(command);
    }

    @Override
    public void revokeUserSessions(String userId, String reason) {
        userSecurityRepository.bumpTokenVersion(userId);
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
