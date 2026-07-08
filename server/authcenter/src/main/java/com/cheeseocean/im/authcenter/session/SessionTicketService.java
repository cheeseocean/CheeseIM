package com.cheeseocean.im.authcenter.session;

import com.cheeseocean.im.authcenter.auth.AccessTokenPrincipal;
import com.cheeseocean.im.authcenter.config.AuthCenterConfig;
import com.cheeseocean.im.authcenter.repository.UserSecurityRepository;
import com.cheeseocean.im.common.api.session.SessionPrincipal;
import com.cheeseocean.im.common.api.dto.user.WsTicketPrincipal;
import com.cheeseocean.im.common.api.enums.SessionStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SessionTicketService {

    private final AuthCenterConfig authCenterConfig;
    private final UserSecurityRepository userSecurityRepository;

    public SessionTicketService(AuthCenterConfig authCenterConfig,
                                UserSecurityRepository userSecurityRepository) {
        this.authCenterConfig = authCenterConfig;
        this.userSecurityRepository = userSecurityRepository;
    }

    public SessionPrincipal buildSession(AccessTokenPrincipal principal, String deviceId, String platform, String clientVersion) {
        SessionPrincipal session = new SessionPrincipal();
        session.setUserId(principal.getUserId());
        session.setTenantId("default");
        String resolvedDeviceId = deviceId == null || deviceId.isBlank() ? principal.getDeviceId() : deviceId;
        session.setSessionId(principal.getSessionId() == null || principal.getSessionId().isBlank()
                ? "sess:" + principal.getUserId() + ":" + resolvedDeviceId
                : principal.getSessionId());
        session.setDeviceId(resolvedDeviceId);
        session.setPlatform(platform == null || platform.isBlank() ? principal.getPlatform() : platform);
        session.setClientVersion(clientVersion);
        session.setTokenVersion(resolveTokenVersion(principal));
        session.setPermissionVersion(1L);
        session.setPasswordVersion(1L);
        session.setStatus(SessionStatus.ACTIVE);
        long now = System.currentTimeMillis();
        session.setLoginAt(now);
        session.setLastActiveAt(now);
        return session;
    }

    public WsTicketPrincipal buildTicket(SessionPrincipal session) {
        WsTicketPrincipal principal = new WsTicketPrincipal();
        principal.setTicket(UUID.randomUUID().toString());
        principal.setUserId(session.getUserId());
        principal.setTenantId(session.getTenantId());
        principal.setSessionId(session.getSessionId());
        principal.setDeviceId(session.getDeviceId());
        principal.setPlatform(session.getPlatform());
        principal.setTokenVersion(session.getTokenVersion());
        principal.setExpireAt(System.currentTimeMillis() + authCenterConfig.getWsTicket().getTtlMs());
        principal.setUsed(false);
        return principal;
    }

    public long wsTicketTtlMs() {
        return authCenterConfig.getWsTicket().getTtlMs();
    }

    private long resolveTokenVersion(AccessTokenPrincipal principal) {
        if (principal.getTokenVersion() != null) {
            return principal.getTokenVersion();
        }
        return userSecurityRepository.tokenVersion(principal.getUserId());
    }
}
