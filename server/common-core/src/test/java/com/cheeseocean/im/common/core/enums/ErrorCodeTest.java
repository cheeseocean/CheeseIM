package com.cheeseocean.im.common.core.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ErrorCodeTest {

    @Test
    void fromCodeShouldResolveMessageSendFailedErrorCode() {
        assertEquals(ErrorCode.MSG_SEND_FAILED, ErrorCode.fromCode(ErrorCode.MSG_SEND_FAILED.getCode()));
    }

    @Test
    void fromCodeShouldRejectUnknownErrorCode() {
        assertThrows(IllegalArgumentException.class, () -> ErrorCode.fromCode(999999));
    }
}
