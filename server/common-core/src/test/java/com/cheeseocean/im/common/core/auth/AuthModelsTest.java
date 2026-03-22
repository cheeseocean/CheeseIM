package com.cheeseocean.im.common.core.auth;

import com.cheeseocean.im.common.core.enums.SessionStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthModelsTest {

    @Test
    void sessionPrincipalShouldReportActiveOnlyForActiveStatus() {
        SessionPrincipal principal = new SessionPrincipal();
        principal.setStatus(SessionStatus.ACTIVE);
        assertTrue(principal.isActive());

        principal.setStatus(SessionStatus.REVOKED);
        assertFalse(principal.isActive());
    }

    @Test
    void permissionCheckResultFactoriesShouldExposeAllowedFlag() {
        assertTrue(PermissionCheckResult.allow().isAllowed());
        assertFalse(PermissionCheckResult.deny("DENIED", "blocked").isAllowed());
    }

    @Test
    void wsTicketPrincipalShouldExpireAtBoundary() {
        WsTicketPrincipal principal = new WsTicketPrincipal();
        principal.setExpireAt(100L);

        assertFalse(principal.isExpired(99L));
        assertTrue(principal.isExpired(100L));
    }
}
