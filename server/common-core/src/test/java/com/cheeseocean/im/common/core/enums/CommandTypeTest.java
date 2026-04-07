package com.cheeseocean.im.common.core.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CommandTypeTest {

    @Test
    void fromCodeShouldResolveChatSendCommandType() {
        assertEquals(com.cheeseocean.im.common.api.enums.CommandType.CHAT_SEND, com.cheeseocean.im.common.api.enums.CommandType.fromCode(com.cheeseocean.im.common.api.enums.CommandType.CHAT_SEND.getCode()));
    }

    @Test
    void fromCodeShouldRejectUnknownCommandType() {
        assertThrows(IllegalArgumentException.class, () -> com.cheeseocean.im.common.api.enums.CommandType.fromCode(999999));
    }
}
