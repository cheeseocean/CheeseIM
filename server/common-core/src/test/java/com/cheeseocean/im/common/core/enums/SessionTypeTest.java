package com.cheeseocean.im.common.core.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionTypeTest {

    @Test
    void fromCodeShouldResolveGroupSessionType() {
        assertEquals(SessionType.GROUP, SessionType.fromCode(SessionType.GROUP.getCode()));
    }

    @Test
    void fromCodeShouldRejectUnknownSessionType() {
        assertThrows(IllegalArgumentException.class, () -> SessionType.fromCode(999999));
    }
}
