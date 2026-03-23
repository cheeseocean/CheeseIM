package com.cheeseocean.im.common.core.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReceiptTypeTest {

    @Test
    void fromCodeShouldResolveReadCursorReceiptType() {
        assertEquals(ReceiptType.READ_CURSOR, ReceiptType.fromCode(ReceiptType.READ_CURSOR.getCode()));
    }

    @Test
    void fromCodeShouldRejectUnknownReceiptType() {
        assertThrows(IllegalArgumentException.class, () -> ReceiptType.fromCode("NOPE"));
    }
}
