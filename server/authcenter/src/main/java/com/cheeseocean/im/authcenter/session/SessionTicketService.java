package com.cheeseocean.im.authcenter.session;

import com.cheeseocean.im.authcenter.auth.AccessTokenPrincipal;
import com.cheeseocean.im.authcenter.config.AuthCenterConfig;
import com.cheeseocean.im.common.core.auth.SessionPrincipal;
import com.cheeseocean.im.common.core.auth.WsTicketPrincipal;
import com.cheeseocean.im.common.core.enums.SessionStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SessionTicketService {

    private final AuthCenterConfig authCenterConfig;

    public SessionTicketService(AuthCenterConfig authCenterConfig) {
        this.authCenterConfig = authCenterConfig;
    }

    public SessionPrincipal buildSession(AccessTokenPrincipal principal, String deviceId, String platform, String clientVersion) {
        SessionPrincipal session = new SessionPrincipal();
        session.setUserId(principal.getUserId());
        session.setTenantId("default");
        session.setSessionId("sess:" + principal.getUserId() + ":" + (deviceId == null || deviceId.isBlank() ? principal.getDeviceId() : deviceId));
        session.setDeviceId(deviceId == null || deviceId.isBlank() ? principal.getDeviceId() : deviceId);
        session.setPlatform(platform == null || platform.isBlank() ? principal.getPlatform() : platform);
        session.setClientVersion(clientVersion);
        session.setTokenVersion(1L);
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
}
