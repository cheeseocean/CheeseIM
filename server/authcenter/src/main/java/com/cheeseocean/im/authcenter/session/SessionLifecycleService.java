package com.cheeseocean.im.authcenter.session;

import com.cheeseocean.im.authcenter.auth.AccessTokenPrincipal;
import com.cheeseocean.im.authcenter.auth.AccessTokenService;
import com.cheeseocean.im.authcenter.auth.JwtTokenIssuer;
import com.cheeseocean.im.authcenter.model.AuthLoginRequest;
import com.cheeseocean.im.authcenter.model.AuthRefreshRequest;
import com.cheeseocean.im.authcenter.model.AuthResponse;
import com.cheeseocean.im.authcenter.repository.RefreshTokenRepository;
import com.cheeseocean.im.authcenter.repository.SessionRepository;
import com.cheeseocean.im.authcenter.repository.UserSecurityRepository;
import com.cheeseocean.im.common.api.session.SessionRevocationService;
import com.cheeseocean.im.common.core.auth.SessionPrincipal;
import com.cheeseocean.im.common.core.enums.SessionStatus;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class SessionLifecycleService {

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

    public AuthResponse login(AuthLoginRequest request) {
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
        principal.setPlatform(platformName(request.getPlatformId()));
        principal.setAccessToken("login-bootstrap");

        SessionPrincipal session = sessionTicketService.buildSession(principal, request.getDeviceId(),
                principal.getPlatform(), request.getClientVersion());
        session.setSessionId("sess:" + UUID.randomUUID());
        session.setStatus(SessionStatus.ACTIVE);

        JwtTokenIssuer.TokenPair tokenPair = jwtTokenIssuer.issue(session);
        sessionRepository.save(session, accessTokenService.getTokenExpirationMs());
        refreshTokenRepository.save(tokenPair.getRefreshToken(), session.getSessionId(), tokenPair.getRefreshExpireAt() - System.currentTimeMillis());

        AuthResponse response = new AuthResponse();
        response.setUserId(session.getUserId());
        response.setSessionId(session.getSessionId());
        response.setAccessToken(tokenPair.getAccessToken());
        response.setRefreshToken(tokenPair.getRefreshToken());
        response.setAccessExpireAt(tokenPair.getAccessExpireAt());
        response.setRefreshExpireAt(tokenPair.getRefreshExpireAt());
        return response;
    }

    public AuthResponse refresh(AuthRefreshRequest request) {
        String sessionId = refreshTokenRepository.getSessionId(request.getRefreshToken());
        if (sessionId == null) {
            throw new IllegalStateException("refresh token invalid");
        }
        SessionPrincipal session = sessionRepository.findBySessionId(sessionId);
        if (session == null || !session.isActive()) {
            throw new IllegalStateException("session invalid");
        }
        JwtTokenIssuer.TokenPair tokenPair = jwtTokenIssuer.issue(session);
        refreshTokenRepository.save(tokenPair.getRefreshToken(), session.getSessionId(), tokenPair.getRefreshExpireAt() - System.currentTimeMillis());

        AuthResponse response = new AuthResponse();
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

    private String platformName(Integer platformId) {
        if (platformId == null) {
            return "unknown";
        }
        switch (platformId) {
            case 1:
                return "ios";
            case 2:
                return "android";
            case 3:
                return "windows";
            case 4:
                return "osx";
            case 5:
                return "web";
            case 6:
                return "miniweb";
            case 7:
                return "linux";
            default:
                return "unknown";
        }
    }
}
