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
import org.springframework.data.mongodb.core.BulkOperations;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlockHistoryPersistenceServiceTest {

    @Test
    void persistShouldWriteAllMessageCoreFieldsIntoSlot() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        BulkOperations mappingOps = mock(BulkOperations.class);
        BulkOperations blockOps = mock(BulkOperations.class);
        when(mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, MessageIdMappingDoc.class)).thenReturn(mappingOps);
        when(mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, MessageBlockDoc.class)).thenReturn(blockOps);
        BlockHistoryPersistenceService service = new BlockHistoryPersistenceService(mongoTemplate);

        HistoryEvent event = new HistoryEvent();
        event.setConversationId("s:u100:u200");
        event.setBeginSeq(101L);
        event.setEndSeq(101L);
        event.setMessages(List.of(message(101L, "smsg-1", "cmsg-1")));

        service.persist(event);

        var queryCaptor = forClass(Query.class);
        var updateCaptor = forClass(Update.class);
        verify(blockOps).upsert(queryCaptor.capture(), updateCaptor.capture());
        verify(blockOps).execute();

        assertEquals("s:u100:u200:1", queryCaptor.getValue().getQueryObject().getString("_id"));
        Document updateObject = updateCaptor.getValue().getUpdateObject();
        Document setDocument = updateObject.get("$set", Document.class);
        assertTrue(setDocument.containsKey("messages.0"));

        MessageSlot slot = (MessageSlot) setDocument.get("messages.0");
        assertEquals(101L, slot.getSeq());
        assertEquals("cmsg-1", slot.getClientMsgId());
        assertEquals("smsg-1", slot.getServerMsgId());
        assertEquals("u100", slot.getSenderId());
        assertEquals("u200", slot.getReceiverId());
        assertEquals("trace-101", slot.getUniqueId());
        assertEquals(ChatType.PRIVATE.getCode(), slot.getSessionType());
        assertEquals(ContentType.TEXT.getCode(), slot.getContentType());
        assertEquals(MessageStatus.ACCEPTED.getCode(), slot.getStatus());
        assertEquals(PlatformType.IOS.getCode(), slot.getPlatformType());
        assertEquals(MessageSource.USER.getCode(), slot.getSource());
        assertEquals(123L, slot.getSendTime());
        assertEquals(456L, slot.getCreateTime());
        assertArrayEquals("hello".getBytes(), slot.getContent());
        assertEquals("zh", slot.getAttributes().get("lang"));
        assertEquals(Boolean.TRUE, slot.getOptions().getNeedHistory());

        var mappingQueryCaptor = forClass(Query.class);
        var mappingUpdateCaptor = forClass(Update.class);
        verify(mappingOps).upsert(mappingQueryCaptor.capture(), mappingUpdateCaptor.capture());
        verify(mappingOps).execute();

        assertEquals("s:u100:u200:cmsg-1", mappingQueryCaptor.getValue().getQueryObject().getString("_id"));
        Document mappingSet = mappingUpdateCaptor.getValue().getUpdateObject().get("$set", Document.class);
        assertEquals("smsg-1", mappingSet.getString("serverMsgId"));
        assertEquals(101L, mappingSet.getLong("seq"));
        assertEquals("u100", mappingSet.getString("senderId"));
    }

    @Test
    void persistShouldGroupMessagesByBlockIntoSingleBulk() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        BulkOperations mappingOps = mock(BulkOperations.class);
        BulkOperations blockOps = mock(BulkOperations.class);
        when(mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, MessageIdMappingDoc.class)).thenReturn(mappingOps);
        when(mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, MessageBlockDoc.class)).thenReturn(blockOps);
        BlockHistoryPersistenceService service = new BlockHistoryPersistenceService(mongoTemplate);

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

        // 跨 2 个 block 也只 execute 一次；3 条 mapping 合入一个 bulk
        verify(blockOps, times(2)).upsert(any(Query.class), any(Update.class));
        verify(blockOps, times(1)).execute();
        verify(mappingOps, times(3)).upsert(any(Query.class), any(Update.class));
        verify(mappingOps, times(1)).execute();
    }

    @Test
    void persistShouldSkipEmptyEvent() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        BlockHistoryPersistenceService service = new BlockHistoryPersistenceService(mongoTemplate);

        HistoryEvent event = new HistoryEvent();
        event.setConversationId("s:u100:u200");
        event.setMessages(List.of());

        service.persist(event);

        verify(mongoTemplate, never()).bulkOps(any(), any(Class.class));
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
