package com.cheeseocean.im.common.core.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CommandTypeTest {

    @Test
    void fromCodeShouldResolveChatSendCommandType() {
        assertEquals(CommandType.CHAT_SEND, CommandType.fromCode(CommandType.CHAT_SEND.getCode()));
    }

    @Test
    void fromCodeShouldRejectUnknownCommandType() {
        assertThrows(IllegalArgumentException.class, () -> CommandType.fromCode(999999));
    }
}
