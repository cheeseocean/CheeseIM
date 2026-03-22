package com.cheeseocean.im.client.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsolePrinterTest {

    @Test
    void formatConversationShouldRenderBubbleLayoutAndStatuses() {
        DemoState state = new DemoState();
        state.session().setUserId("userA");
        state.recordOutgoingText("userB", "client-1", "hello userB");
        state.confirmOutgoing("client-1", "msg-1");
        state.recordIncomingText("userB", "msg-2", 7L, "hi userA");
        state.markPeerTyping("userB", true);
        state.markRead("c1:userA:userB", 7L);

        String rendered = new ConsolePrinter().formatConversation(state, "userB");

        assertTrue(rendered.contains("Conversation with userB"));
        assertTrue(rendered.contains("hello userB"));
        assertTrue(rendered.contains("hi userA"));
        assertTrue(rendered.contains("[read]"));
        assertTrue(rendered.contains("typing"));
    }

    @Test
    void formatConversationShouldRenderRevokedMessageBody() {
        DemoState state = new DemoState();
        state.session().setUserId("userA");
        state.recordOutgoingText("userB", "client-1", "temporary");
        state.confirmOutgoing("client-1", "msg-1");
        state.markRevoked("c1:userA:userB", "msg-1");

        String rendered = new ConsolePrinter().formatConversation(state, "userB");

        assertTrue(rendered.contains("[recalled]"));
    }
}
