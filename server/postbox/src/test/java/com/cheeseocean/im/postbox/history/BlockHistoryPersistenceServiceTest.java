package com.cheeseocean.im.postbox.history;

import com.cheeseocean.im.common.api.dto.message.MessageOptions;
import com.cheeseocean.im.common.api.dto.message.SequencedMessage;
import com.cheeseocean.im.common.api.event.HistoryEvent;
import com.cheeseocean.im.common.core.enums.ContentType;
import com.cheeseocean.im.common.core.enums.SessionType;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BlockHistoryPersistenceServiceTest {

    @Test
    void historyEventPersistsMessagesIntoFixedBlockSlotsAndMapping() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        MessageIdMappingRepository mappingRepository = mock(MessageIdMappingRepository.class);
        BlockHistoryPersistenceService service = new BlockHistoryPersistenceService(mongoTemplate, mappingRepository);

        SequencedMessage message = message(101L, "smsg-1", "cmsg-1");
        HistoryEvent event = new HistoryEvent();
        event.setConversationId("c1:u100:u200");
        event.setBeginSeq(101L);
        event.setEndSeq(101L);
        event.setMessages(List.of(message));

        service.persist(event);

        var queryCaptor = forClass(Query.class);
        var updateCaptor = forClass(Update.class);
        verify(mongoTemplate).upsert(queryCaptor.capture(), updateCaptor.capture(), eq(MessageBlockDoc.class));
        verify(mappingRepository).save(any(MessageIdMappingDoc.class));

        assertEquals("c1:u100:u200:1", queryCaptor.getValue().getQueryObject().getString("_id"));
        org.bson.Document updateObject = updateCaptor.getValue().getUpdateObject();
        org.bson.Document setDocument = updateObject.get("$set", org.bson.Document.class);
        org.bson.Document setOnInsertDocument = updateObject.get("$setOnInsert", org.bson.Document.class);
        org.junit.jupiter.api.Assertions.assertTrue(setDocument.containsKey("messages.0"));
        org.junit.jupiter.api.Assertions.assertTrue(setOnInsertDocument.containsKey("startSeq"));
        org.junit.jupiter.api.Assertions.assertTrue(setOnInsertDocument.containsKey("endSeq"));
    }

    @Test
    void historyEventGroupsMessagesByBlockBeforePersisting() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        MessageIdMappingRepository mappingRepository = mock(MessageIdMappingRepository.class);
        BlockHistoryPersistenceService service = new BlockHistoryPersistenceService(mongoTemplate, mappingRepository);

        HistoryEvent event = new HistoryEvent();
        event.setConversationId("c1:u100:u200");
        event.setBeginSeq(99L);
        event.setEndSeq(101L);
        event.setMessages(List.of(
                message(99L, "s1", "c1"),
                message(100L, "s2", "c2"),
                message(101L, "s3", "c3")
        ));

        service.persist(event);

        verify(mongoTemplate, org.mockito.Mockito.times(2))
                .upsert(any(Query.class), any(Update.class), eq(MessageBlockDoc.class));
        verify(mappingRepository, org.mockito.Mockito.times(3)).save(any(MessageIdMappingDoc.class));
    }

    private static SequencedMessage message(long seq, String serverMsgId, String clientMsgId) {
        MessageOptions options = new MessageOptions();
        options.setNeedHistory(true);

        SequencedMessage message = new SequencedMessage();
        message.setConversationId("c1:u100:u200");
        message.setSeq(seq);
        message.setServerMsgId(serverMsgId);
        message.setClientMsgId(clientMsgId);
        message.setSenderId("u100");
        message.setRecvId("u200");
        message.setSessionType(SessionType.SINGLE.getCode());
        message.setContentType(ContentType.TEXT.getCode());
        message.setContent("hello");
        message.setSendTime(123L);
        message.setOptions(options);
        return message;
    }
}
