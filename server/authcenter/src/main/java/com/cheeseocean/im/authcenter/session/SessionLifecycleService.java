package com.cheeseocean.im.authcenter.session;

import com.cheeseocean.im.authcenter.auth.AccessTokenPrincipal;
import com.cheeseocean.im.authcenter.auth.AccessTokenService;
import com.cheeseocean.im.authcenter.auth.JwtTokenIssuer;
import com.cheeseocean.im.common.api.auth.AuthenticationCommand;
import com.cheeseocean.im.common.api.auth.AuthenticationResult;
import com.cheeseocean.im.common.api.auth.AuthenticationService;
import com.cheeseocean.im.authcenter.repository.RefreshTokenRepository;
import com.cheeseocean.im.authcenter.repository.SessionRepository;
import com.cheeseocean.im.authcenter.repository.UserSecurityRepository;
import com.cheeseocean.im.common.api.session.SessionRevocationService;
import com.cheeseocean.im.common.api.session.SessionPrincipal;
import com.cheeseocean.im.common.api.enums.PlatformType;
import com.cheeseocean.im.common.api.enums.SessionStatus;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@DubboService
public class SessionLifecycleService implements AuthenticationService {

    private final SessionRepository sessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserSecurityRepository userSecurityRepository;
    private final SessionTicketService sessionTicketService;
    private final JwtTokenIssuer jwtTokenIssuer;
    private final AccessTokenService accessTokenService;

    @DubboReference(check = false)
    private SessionRevocationService sessionRevocationService;

    public SessionLifecycleService(SessionRepository sessionRepository,
                                   RefreshTokenRepository refreshTokenRepository,
                                   UserSecurityRepository userSecurityRepository,
                                   SessionTicketService sessionTicketService,
                                   JwtTokenIssuer jwtTokenIssuer,
                                   AccessTokenService accessTokenService) {
        this.sessionRepository = sessionRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userSecurityRepository = userSecurityRepository;
        this.sessionTicketService = sessionTicketService;
        this.jwtTokenIssuer = jwtTokenIssuer;
        this.accessTokenService = accessTokenService;
    }

    public AuthenticationResult login(AuthenticationCommand request) {
        if (request.getUserId() == null || request.getUserId().isBlank()) {
            throw new IllegalStateException("userId required");
        }
        if (userSecurityRepository.isBanned(request.getUserId())) {
            throw new IllegalStateException("user banned");
        }

        AccessTokenPrincipal principal = new AccessTokenPrincipal();
        principal.setUserId(request.getUserId());
        principal.setPlatformId(request.getPlatformId());
        principal.setDeviceId(request.getDeviceId());
        principal.setPlatform(PlatformType.fromCode(request.getPlatformId()).getWireName());
        principal.setAccessToken("login-bootstrap");
        principal.setTokenVersion(userSecurityRepository.tokenVersion(request.getUserId()));

        SessionPrincipal session = sessionTicketService.buildSession(principal, request.getDeviceId(),
                principal.getPlatform(), request.getClientVersion());
        session.setSessionId("sess:" + UUID.randomUUID());
        principal.setSessionId(session.getSessionId());
        session.setStatus(SessionStatus.ACTIVE);

        JwtTokenIssuer.TokenPair tokenPair = jwtTokenIssuer.issue(session);
        sessionRepository.save(session, accessTokenService.getTokenExpirationMs());
        refreshTokenRepository.save(tokenPair.getRefreshToken(), session.getSessionId(), tokenPair.getRefreshExpireAt() - System.currentTimeMillis());

        AuthenticationResult response = new AuthenticationResult();
        response.setUserId(session.getUserId());
        response.setSessionId(session.getSessionId());
        response.setAccessToken(tokenPair.getAccessToken());
        response.setRefreshToken(tokenPair.getRefreshToken());
        response.setAccessExpireAt(tokenPair.getAccessExpireAt());
        response.setRefreshExpireAt(tokenPair.getRefreshExpireAt());
        return response;
    }

    public AuthenticationResult refresh(String refreshToken) {
        String sessionId = refreshTokenRepository.getSessionId(refreshToken);
        if (sessionId == null) {
            throw new IllegalStateException("refresh token invalid");
        }
        SessionPrincipal session = sessionRepository.findBySessionId(sessionId);
        if (session == null || !session.isActive()) {
            throw new IllegalStateException("session invalid");
        }
        if (userSecurityRepository.isBanned(session.getUserId())) {
            throw new IllegalStateException("user banned");
        }
        if (!userSecurityRepository.matchesTokenVersion(session.getUserId(), session.getTokenVersion())) {
            throw new IllegalStateException("token version mismatch");
        }
        JwtTokenIssuer.TokenPair tokenPair = jwtTokenIssuer.issue(session);
        refreshTokenRepository.save(tokenPair.getRefreshToken(), session.getSessionId(), tokenPair.getRefreshExpireAt() - System.currentTimeMillis());

        AuthenticationResult response = new AuthenticationResult();
        response.setUserId(session.getUserId());
        response.setSessionId(session.getSessionId());
        response.setAccessToken(tokenPair.getAccessToken());
        response.setRefreshToken(tokenPair.getRefreshToken());
        response.setAccessExpireAt(tokenPair.getAccessExpireAt());
        response.setRefreshExpireAt(tokenPair.getRefreshExpireAt());
        return response;
    }

    public void logout(String sessionId) {
        sessionRevocationService.revokeSession(sessionId, "logout");
    }

    public void kickoffDevice(String userId, String deviceId) {
        sessionRevocationService.revokeDeviceSession(userId, deviceId, "device kicked");
    }

    public void kickoffAll(String userId) {
        sessionRevocationService.revokeUserSessions(userId, "kickoff all");
    }
}
