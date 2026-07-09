package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.postbox.history.AttachmentMetadataDoc;
import com.cheeseocean.im.postbox.history.MessageBlockDoc;
import com.cheeseocean.im.postbox.history.MessageSlot;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlockMessageQueryServiceTest {

    @Test
    void findSlotShouldPointLookupBlockByDocId() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        MessageBlockDoc block = new MessageBlockDoc();
        block.setMessages(List.of(slot(101L)));
        when(mongoTemplate.findOne(any(Query.class), eq(MessageBlockDoc.class))).thenReturn(block);
        BlockMessageQueryService service = new BlockMessageQueryService(mongoTemplate);

        MessageSlot found = service.findSlot("s:u100:u200", 101L);

        assertEquals(101L, found.getSeq());
        var queryCaptor = forClass(Query.class);
        verify(mongoTemplate).findOne(queryCaptor.capture(), eq(MessageBlockDoc.class));
        // seq=101 属于 blockNo=1（BlockIndexUtil 0 起算），点查 _id 而非 conversationId+blockNo 扫描
        assertEquals("s:u100:u200:1", queryCaptor.getValue().getQueryObject().getString("_id"));
    }

    @Test
    void findAttachmentCandidateShouldResolveViaMetadataPointLookup() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        AttachmentMetadataDoc metadata = new AttachmentMetadataDoc();
        metadata.setId("att-1");
        metadata.setConversationId("s:u100:u200");
        metadata.setServerMsgId("smsg-1");
        metadata.setSeq(101L);
        when(mongoTemplate.findById("att-1", AttachmentMetadataDoc.class)).thenReturn(metadata);

        MessageBlockDoc block = new MessageBlockDoc();
        MessageSlot slot = slot(101L);
        slot.setContent("{\"attachmentId\":\"att-1\",\"storageKey\":\"oss/a\"}".getBytes());
        block.setMessages(List.of(slot));
        when(mongoTemplate.findOne(any(Query.class), eq(MessageBlockDoc.class))).thenReturn(block);
        BlockMessageQueryService service = new BlockMessageQueryService(mongoTemplate);

        Optional<BlockMessageQueryService.AttachmentMessageCandidate> candidate =
                service.findAttachmentCandidate("att-1");

        assertTrue(candidate.isPresent());
        assertEquals("s:u100:u200", candidate.get().conversationId());
        assertEquals("smsg-1", candidate.get().serverMsgId());
        assertTrue(candidate.get().content().contains("oss/a"));
    }

    @Test
    void findAttachmentCandidateShouldReturnEmptyWhenMetadataMissing() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        when(mongoTemplate.findById("att-x", AttachmentMetadataDoc.class)).thenReturn(null);
        BlockMessageQueryService service = new BlockMessageQueryService(mongoTemplate);

        assertTrue(service.findAttachmentCandidate("att-x").isEmpty());
        assertTrue(service.findAttachmentCandidate(" ").isEmpty());
        verify(mongoTemplate, never()).findOne(any(Query.class), eq(MessageBlockDoc.class));
    }

    private static MessageSlot slot(long seq) {
        MessageSlot slot = new MessageSlot();
        slot.setSeq(seq);
        slot.setServerMsgId("smsg-1");
        slot.setSenderId("u100");
        return slot;
    }
}
