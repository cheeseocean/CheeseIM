package com.cheeseocean.im.common.api.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionStatusTest {

    @Test
    void fromCodeShouldResolveStablePersistentCode() {
        assertEquals(SessionStatus.ACTIVE, SessionStatus.fromCode(1));
        assertEquals(SessionStatus.RISK_LOCKED, SessionStatus.fromCode(6));
    }

    @Test
    void fromCodeShouldRejectUnknownCode() {
        assertThrows(IllegalArgumentException.class, () -> SessionStatus.fromCode(0));
    }
}
