package com.cheeseocean.im.authcenter.session;

import com.cheeseocean.im.authcenter.auth.AccessTokenPrincipal;
import com.cheeseocean.im.authcenter.auth.JwtTokenIssuer;
import com.cheeseocean.im.authcenter.config.AuthCenterConfig;
import com.cheeseocean.im.authcenter.identity.LoginIdentityVerifier;
import com.cheeseocean.im.authcenter.identity.VerifiedLoginIdentity;
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
import com.cheeseocean.im.common.api.enums.ErrorCode;
import com.cheeseocean.im.common.api.exception.BusinessException;
import com.cheeseocean.im.common.core.store.session.refresh.RefreshTokenStateStore;
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
    private final SessionRevocationService sessionRevocationService;
    private final AuthCenterConfig authCenterConfig;
    private final LoginIdentityVerifier loginIdentityVerifier;

    public SessionLifecycleService(SessionRepository sessionRepository,
                                   RefreshTokenRepository refreshTokenRepository,
                                   UserSecurityRepository userSecurityRepository,
                                   SessionTicketService sessionTicketService,
                                   JwtTokenIssuer jwtTokenIssuer,
                                   SessionRevocationService sessionRevocationService,
                                   AuthCenterConfig authCenterConfig,
                                   LoginIdentityVerifier loginIdentityVerifier) {
        this.sessionRepository = sessionRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userSecurityRepository = userSecurityRepository;
        this.sessionTicketService = sessionTicketService;
        this.jwtTokenIssuer = jwtTokenIssuer;
        this.sessionRevocationService = sessionRevocationService;
        this.authCenterConfig = authCenterConfig;
        this.loginIdentityVerifier = loginIdentityVerifier;
    }

    public AuthenticationResult login(AuthenticationCommand request) {
        validateLoginContext(request);
        VerifiedLoginIdentity identity = loginIdentityVerifier.verify(request);
        String userId = identity.userId();
        if (userSecurityRepository.isBanned(userId)) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_FAILED);
        }

        AccessTokenPrincipal principal = new AccessTokenPrincipal();
        principal.setUserId(userId);
        principal.setPlatformId(request.getPlatformId());
        principal.setDeviceId(request.getDeviceId());
        principal.setPlatform(PlatformType.fromCode(request.getPlatformId()).getWireName());
        principal.setAccessToken("login-bootstrap");
        principal.setTokenVersion(userSecurityRepository.tokenVersion(userId));

        SessionPrincipal session = sessionTicketService.buildSession(principal, request.getDeviceId(),
                principal.getPlatform(), request.getClientVersion());
        session.setSessionId("sess:" + UUID.randomUUID());
        principal.setSessionId(session.getSessionId());
        session.setStatus(SessionStatus.ACTIVE);

        long nowMillis = System.currentTimeMillis();
        RefreshTokenStateStore.IssuedToken issuedToken = refreshTokenRepository.createFamily(
                session.getSessionId(),
                authCenterConfig.getRefreshToken().getTtlMs(),
                nowMillis);
        session.setRefreshTokenFamilyId(issuedToken.familyId());
        session.setRefreshTokenExpireAt(issuedToken.expiresAt());
        try {
            JwtTokenIssuer.TokenPair tokenPair = jwtTokenIssuer.issue(
                    session,
                    issuedToken.refreshToken(),
                    issuedToken.expiresAt());
            sessionRepository.save(session);
            return buildResult(session, tokenPair);
        } catch (RuntimeException exception) {
            refreshTokenRepository.revokeFamily(issuedToken.familyId());
            throw exception;
        }
    }

    /**
     * 在一次性 assertion 被消费前完成设备上下文校验，避免参数错误烧掉合法凭据。
     */
    private void validateLoginContext(AuthenticationCommand request) {
        if (request == null
                || request.getDeviceId() == null || request.getDeviceId().isBlank()
                || PlatformType.fromCode(request.getPlatformId()) == PlatformType.UNKNOWN) {
            throw new BusinessException(ErrorCode.INVALID_PARAM);
        }
    }

    public AuthenticationResult refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalStateException("refresh token invalid");
        }

        long nowMillis = System.currentTimeMillis();
        RefreshTokenStateStore.Inspection inspection = refreshTokenRepository.inspect(refreshToken, nowMillis);
        if (inspection.status() == RefreshTokenStateStore.TokenStatus.REUSED) {
            refreshTokenRepository.rotate(refreshToken, nowMillis);
            compromiseSession(inspection.sessionId());
            throw new IllegalStateException("refresh token reuse detected");
        }
        if (inspection.status() != RefreshTokenStateStore.TokenStatus.CURRENT) {
            throw new IllegalStateException("refresh token invalid");
        }

        SessionPrincipal session = requireValidSession(inspection.sessionId(), inspection.familyId());
        RefreshTokenStateStore.Rotation rotation = refreshTokenRepository.rotate(refreshToken, nowMillis);
        if (rotation.status() == RefreshTokenStateStore.RotationStatus.REUSED) {
            compromiseSession(rotation.sessionId());
            throw new IllegalStateException("refresh token reuse detected");
        }
        if (rotation.status() != RefreshTokenStateStore.RotationStatus.ROTATED) {
            throw new IllegalStateException("refresh token invalid");
        }

        try {
            session = requireValidSession(rotation.sessionId(), rotation.familyId());
            JwtTokenIssuer.TokenPair tokenPair = jwtTokenIssuer.issue(
                    session,
                    rotation.refreshToken(),
                    rotation.expiresAt());
            return buildResult(session, tokenPair);
        } catch (RuntimeException exception) {
            refreshTokenRepository.revokeFamily(rotation.familyId());
            compromiseSession(rotation.sessionId());
            throw exception;
        }
    }

    private SessionPrincipal requireValidSession(String sessionId, String familyId) {
        SessionPrincipal session = sessionRepository.findBySessionId(sessionId);
        if (session == null || !session.isActive()) {
            refreshTokenRepository.revokeFamily(familyId);
            throw new IllegalStateException("session invalid");
        }
        if (!familyId.equals(session.getRefreshTokenFamilyId())) {
            refreshTokenRepository.revokeFamily(familyId);
            throw new IllegalStateException("refresh token family mismatch");
        }
        if (userSecurityRepository.isBanned(session.getUserId())) {
            refreshTokenRepository.revokeFamily(familyId);
            throw new IllegalStateException("user banned");
        }
        if (!userSecurityRepository.matchesTokenVersion(session.getUserId(), session.getTokenVersion())) {
            refreshTokenRepository.revokeFamily(familyId);
            throw new IllegalStateException("token version mismatch");
        }
        return session;
    }

    private void compromiseSession(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            sessionRevocationService.revokeSession(sessionId, "refresh token reuse detected");
        }
    }

    private AuthenticationResult buildResult(SessionPrincipal session, JwtTokenIssuer.TokenPair tokenPair) {
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
