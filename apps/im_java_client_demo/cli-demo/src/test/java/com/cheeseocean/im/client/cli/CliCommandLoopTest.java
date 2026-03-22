package com.cheeseocean.im.client.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CliCommandLoopTest {

    @Test
    void parseLoginCommandShouldExtractUserPasswordAndPlatform() {
        ParsedCommand command = CliCommandLoop.parse("login userA secret 2");

        assertEquals(ParsedCommand.Type.LOGIN, command.type());
        assertEquals("userA", command.arg1());
        assertEquals("secret", command.arg2());
        assertEquals("2", command.arg3());
    }

    @Test
    void parseSendCommandShouldPreserveRemainingTextAsMessageBody() {
        ParsedCommand command = CliCommandLoop.parse("send userB hello tcp world");

        assertEquals(ParsedCommand.Type.SEND, command.type());
        assertEquals("userB", command.arg1());
        assertEquals("hello tcp world", command.arg2());
    }

    @Test
    void parseUnknownCommandShouldReturnHelpCommand() {
        ParsedCommand command = CliCommandLoop.parse("what-is-this");

        assertEquals(ParsedCommand.Type.HELP, command.type());
    }

    @Test
    void formatInboundMessageShouldExtractReadableFields() {
        String formatted = CliCommandLoop.LoggingListener.formatInboundMessage(
                "{\"serverMsgId\":\"msg-1\",\"clientMsgID\":\"client-1\",\"sendID\":\"userA\",\"recvID\":\"userB\",\"content\":\"hello\",\"contentType\":101,\"sessionType\":1,\"sendTime\":1710000000002}"
        );

        assertEquals("from=userA to=userB content=hello serverMsgId=msg-1 seq=-", formatted);
    }
}
