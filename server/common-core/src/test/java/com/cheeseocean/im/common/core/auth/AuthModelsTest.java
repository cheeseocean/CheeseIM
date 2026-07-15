package com.cheeseocean.im.common.core.auth;

import com.cheeseocean.im.common.api.enums.SessionStatus;
import com.cheeseocean.im.common.api.permission.PermissionCheckResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthModelsTest {

    @Test
    void sessionPrincipalShouldReportActiveOnlyForActiveStatus() {
        com.cheeseocean.im.common.api.session.SessionPrincipal principal = new com.cheeseocean.im.common.api.session.SessionPrincipal();
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
        com.cheeseocean.im.common.api.dto.user.WsTicketPrincipal principal = new com.cheeseocean.im.common.api.dto.user.WsTicketPrincipal();
        principal.setExpireAt(100L);

        assertFalse(principal.isExpired(99L));
        assertTrue(principal.isExpired(100L));
    }
}
