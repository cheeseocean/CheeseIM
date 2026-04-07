package com.cheeseocean.im.common.core.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionTypeTest {

    @Test
    void fromCodeShouldResolveGroupSessionType() {
        assertEquals(com.cheeseocean.im.common.api.enums.SessionType.GROUP, com.cheeseocean.im.common.api.enums.SessionType.fromCode(com.cheeseocean.im.common.api.enums.SessionType.GROUP.getCode()));
    }

    @Test
    void fromCodeShouldRejectUnknownSessionType() {
        assertThrows(IllegalArgumentException.class, () -> com.cheeseocean.im.common.api.enums.SessionType.fromCode(999999));
    }
}
