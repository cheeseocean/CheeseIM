package com.cheeseocean.im.postbox.listener;

import com.cheeseocean.im.common.api.dto.message.MessageOptions;
import com.cheeseocean.im.common.api.dto.message.SequencedMessage;
import com.cheeseocean.im.common.api.event.HistoryEvent;
import com.cheeseocean.im.common.core.enums.ContentType;
import com.cheeseocean.im.common.core.enums.SessionType;
import com.cheeseocean.im.postbox.history.BlockHistoryPersistenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class HistoryEventListenerTest {

    @Test
    void onMessageShouldPersistHistoryEvent() throws Exception {
        BlockHistoryPersistenceService persistenceService = mock(BlockHistoryPersistenceService.class);
        HistoryEventListener listener = new HistoryEventListener(new ObjectMapper(), persistenceService);

        listener.onMessage(new ObjectMapper().writeValueAsString(event()));

        verify(persistenceService).persist(org.mockito.ArgumentMatchers.any(HistoryEvent.class));
    }

    private static HistoryEvent event() {
        MessageOptions options = new MessageOptions();
        options.setNeedHistory(true);
        SequencedMessage message = new SequencedMessage();
        message.setConversationId("c1:u100:u200");
        message.setSeq(1L);
        message.setServerMsgId("s1");
        message.setClientMsgId("c1");
        message.setSenderId("u100");
        message.setRecvId("u200");
        message.setSessionType(SessionType.SINGLE.getCode());
        message.setContentType(ContentType.TEXT.getCode());
        message.setContent("hello");
        message.setOptions(options);

        HistoryEvent event = new HistoryEvent();
        event.setConversationId("c1:u100:u200");
        event.setBeginSeq(1L);
        event.setEndSeq(1L);
        event.setMessages(List.of(message));
        return event;
    }
}
