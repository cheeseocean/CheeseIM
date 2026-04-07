package com.cheeseocean.im.common.core.util;

import com.cheeseocean.im.common.api.enums.SessionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConversationIdUtilTest {

    @Test
    void singleConversationIdIsOrderIndependent() {
        assertEquals("c1:u100:u200", ConversationIdUtil.single("u200", "u100"));
    }

    @Test
    void buildConversationIdSupportsGroupAndNotification() {
        assertEquals("c2:g1", ConversationIdUtil.buildConversationId(SessionType.GROUP.getCode(), "u1", null, "g1"));
        assertEquals("c3:u9", ConversationIdUtil.buildConversationId(SessionType.NOTIFICATION.getCode(), null, "u9", null));
    }

    @Test
    void unsupportedSessionTypeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> ConversationIdUtil.buildConversationId(999, "u1", "u2", null));
    }
}
