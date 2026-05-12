package com.cheeseocean.im.common.core.enums;

import com.cheeseocean.im.common.api.enums.ChatType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChatTypeTest {

    @Test
    void fromCodeShouldResolveGroupSessionType() {
        assertEquals(ChatType.GROUP, ChatType.fromCode(ChatType.GROUP.getCode()));
    }

    @Test
    void fromCodeShouldRejectUnknownSessionType() {
        assertThrows(IllegalArgumentException.class, () -> ChatType.fromCode(999999));
    }
}
