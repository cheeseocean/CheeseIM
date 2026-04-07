package com.cheeseocean.im.common.core.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ErrorCodeTest {

    @Test
    void fromCodeShouldResolveMessageSendFailedErrorCode() {
        assertEquals(com.cheeseocean.im.common.api.enums.ErrorCode.MSG_SEND_FAILED, com.cheeseocean.im.common.api.enums.ErrorCode.fromCode(com.cheeseocean.im.common.api.enums.ErrorCode.MSG_SEND_FAILED.getCode()));
    }

    @Test
    void fromCodeShouldRejectUnknownErrorCode() {
        assertThrows(IllegalArgumentException.class, () -> com.cheeseocean.im.common.api.enums.ErrorCode.fromCode(999999));
    }
}
