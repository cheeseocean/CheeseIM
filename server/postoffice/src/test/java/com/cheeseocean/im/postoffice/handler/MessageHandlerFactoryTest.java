package com.cheeseocean.im.postoffice.handler;

import com.cheeseocean.im.common.core.enums.CommandType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MessageHandlerFactoryTest {

    @Test
    void shouldDispatchHandlersByCommandType() {
        MessageHandler authHandler = mock(MessageHandler.class);
        MessageHandler heartbeatHandler = mock(MessageHandler.class);
        MessageHandler chatHandler = mock(MessageHandler.class);
        MessageHandler receiptHandler = mock(MessageHandler.class);

        when(authHandler.getSupportedCommand()).thenReturn(CommandType.AUTH);
        when(heartbeatHandler.getSupportedCommand()).thenReturn(CommandType.HEARTBEAT);
        when(chatHandler.getSupportedCommand()).thenReturn(CommandType.CHAT_SEND);
        when(receiptHandler.getSupportedCommand()).thenReturn(CommandType.READ_RECEIPT);

        MessageHandlerFactory factory = new MessageHandlerFactory();
        ReflectionTestUtils.setField(factory, "messageHandlers", List.of(authHandler, heartbeatHandler, chatHandler, receiptHandler));
        factory.init();

        assertSame(authHandler, factory.getHandler(CommandType.AUTH));
        assertSame(heartbeatHandler, factory.getHandler(CommandType.HEARTBEAT));
        assertSame(chatHandler, factory.getHandler(CommandType.CHAT_SEND));
        assertSame(receiptHandler, factory.getHandler(CommandType.READ_RECEIPT));
        assertTrue(factory.isSupported(CommandType.AUTH));
        assertFalse(factory.isSupported(CommandType.CHAT_REVOKE));
    }
}
