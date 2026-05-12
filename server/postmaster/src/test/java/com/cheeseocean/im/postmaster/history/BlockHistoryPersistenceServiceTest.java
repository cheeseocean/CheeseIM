package com.cheeseocean.im.postmaster.history;

import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.dto.message.MessageOptions;
import com.cheeseocean.im.common.api.enums.ChatType;
import com.cheeseocean.im.common.api.enums.ContentType;
import com.cheeseocean.im.common.api.enums.MessageSource;
import com.cheeseocean.im.common.api.enums.MessageStatus;
import com.cheeseocean.im.common.api.enums.PlatformType;
import com.cheeseocean.im.common.api.event.HistoryEvent;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BlockHistoryPersistenceServiceTest {

    @Test
    void persistShouldWriteAllMessageCoreFieldsIntoSlot() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        MessageIdMappingRepository mappingRepository = mock(MessageIdMappingRepository.class);
        BlockHistoryPersistenceService service = new BlockHistoryPersistenceService(mongoTemplate, mappingRepository);

        HistoryEvent event = new HistoryEvent();
        event.setConversationId("s:u100:u200");
        event.setBeginSeq(101L);
        event.setEndSeq(101L);
        event.setMessages(List.of(message(101L, "smsg-1", "cmsg-1")));

        service.persist(event);

        var queryCaptor = forClass(Query.class);
        var updateCaptor = forClass(Update.class);
        verify(mongoTemplate).upsert(queryCaptor.capture(), updateCaptor.capture(), eq(MessageBlockDoc.class));
        verify(mappingRepository).save(any(MessageIdMappingDoc.class));

        assertEquals("s:u100:u200:1", queryCaptor.getValue().getQueryObject().getString("_id"));
        Document updateObject = updateCaptor.getValue().getUpdateObject();
        Document setDocument = updateObject.get("$set", Document.class);
        assertTrue(setDocument.containsKey("messages.0"));

        Document slot = setDocument.get("messages.0", Document.class);
        assertEquals(101L, slot.getLong("seq"));
        assertEquals("cmsg-1", slot.getString("clientMsgId"));
        assertEquals("smsg-1", slot.getString("serverMsgId"));
        assertEquals("u100", slot.getString("senderId"));
        assertEquals("u200", slot.getString("receiverId"));
        assertEquals("trace-101", slot.getString("uniqueId"));
        assertEquals(ChatType.PRIVATE.getCode(), slot.getInteger("sessionType"));
        assertEquals(ContentType.TEXT.getCode(), slot.getInteger("contentType"));
        assertEquals(MessageStatus.ACCEPTED.getCode(), slot.getInteger("status"));
        assertEquals(PlatformType.IOS.getCode(), slot.getInteger("platformType"));
        assertEquals(MessageSource.USER.getCode(), slot.getInteger("source"));
        assertEquals(123L, slot.getLong("sendTime"));
        assertEquals(456L, slot.getLong("createTime"));
        assertArrayEquals("hello".getBytes(), slot.get("content", org.bson.types.Binary.class).getData());
        assertEquals("zh", slot.get("attributes", Document.class).getString("lang"));
        assertEquals(Boolean.TRUE, slot.get("options", Document.class).getBoolean("needHistory"));
    }

    @Test
    void persistShouldGroupMessagesByBlockBeforePersisting() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        MessageIdMappingRepository mappingRepository = mock(MessageIdMappingRepository.class);
        BlockHistoryPersistenceService service = new BlockHistoryPersistenceService(mongoTemplate, mappingRepository);

        HistoryEvent event = new HistoryEvent();
        event.setConversationId("s:u100:u200");
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

    private static Message message(long seq, String serverMsgId, String clientMsgId) {
        MessageOptions options = new MessageOptions();
        options.setNeedHistory(true);

        Message message = new Message();
        message.setSeq(seq);
        message.setServerMsgId(serverMsgId);
        message.setClientMsgId(clientMsgId);
        message.setSenderId("u100");
        message.setReceiverId("u200");
        message.setChatType(ChatType.PRIVATE);
        message.setContentType(ContentType.TEXT);
        message.setStatus(MessageStatus.ACCEPTED);
        message.setPlatformType(PlatformType.IOS);
        message.setSource(MessageSource.USER);
        message.setUniqueId("trace-" + seq);
        message.setContent("hello".getBytes());
        message.setSendTime(123L);
        message.setCreateTime(456L);
        message.setOptions(options);
        message.setAttributes(Map.of("lang", "zh"));
        return message;
    }
}
