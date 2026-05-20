package com.cheeseocean.im.common.api.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlatformTypeTest {

    @Test
    void fromNameRecognizesCliWireName() {
        assertEquals(PlatformType.CLI, PlatformType.fromName("cli"));
    }
}
