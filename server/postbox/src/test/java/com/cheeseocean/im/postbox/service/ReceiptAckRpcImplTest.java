package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.api.dto.receipt.ReceiptAckReq;
import com.cheeseocean.im.common.core.enums.ReceiptType;
import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.postbox.history.MessageIdMappingDoc;
import com.cheeseocean.im.postbox.history.MessageIdMappingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReceiptAckRpcImplTest {

    @Test
    void applyShouldAdvanceReadSeqForReadCursorReceipt() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        ConversationReceiptService conversationReceiptService = mock(ConversationReceiptService.class);
        ReceiptAckRpcImpl service = new ReceiptAckRpcImpl(
                conversationReceiptService,
                redisTemplate,
                mock(MessageIdMappingRepository.class));

        ReceiptAckReq req = new ReceiptAckReq();
        req.setAckType(ReceiptType.READ_CURSOR);
        req.setUserId("userB");
        req.setConversationId("single:userA:userB");
        req.setSeq(19L);

        service.apply(req);

        verify(conversationReceiptService).applyReadCursor("userB", "single:userA:userB", 19L);
    }

    @Test
    void applyShouldResolveSeqFromMappingForDeliveredReceipt() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        MessageIdMappingRepository mappingRepository = mock(MessageIdMappingRepository.class);
        MessageIdMappingDoc mapping = new MessageIdMappingDoc();
        mapping.setServerMsgId("msg-1");
        mapping.setConversationId("single:userA:userB");
        mapping.setSeq(11L);
        when(mappingRepository.findByServerMsgId("msg-1")).thenReturn(Optional.of(mapping));

        ReceiptAckRpcImpl service = new ReceiptAckRpcImpl(
                mock(ConversationReceiptService.class),
                redisTemplate,
                mappingRepository);

        ReceiptAckReq req = new ReceiptAckReq();
        req.setAckType(ReceiptType.RECEIVED);
        req.setUserId("userB");
        req.setServerMsgId("msg-1");

        service.apply(req);

        verify(valueOperations).set(eq(RedisKeys.userMaxSeq("userB", "single:userA:userB")), eq("11"));
    }

    @Test
    void applyShouldIgnoreDeliveredReceiptWhenMappingIsMissing() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        MessageIdMappingRepository mappingRepository = mock(MessageIdMappingRepository.class);
        when(mappingRepository.findByServerMsgId("missing")).thenReturn(Optional.empty());

        ReceiptAckRpcImpl service = new ReceiptAckRpcImpl(
                mock(ConversationReceiptService.class),
                redisTemplate,
                mappingRepository);

        ReceiptAckReq req = new ReceiptAckReq();
        req.setAckType(ReceiptType.RECEIVED);
        req.setUserId("userB");
        req.setServerMsgId("missing");

        service.apply(req);

        verify(valueOperations, never()).set(eq(RedisKeys.userMaxSeq("userB", "single:userA:userB")), eq("11"));
    }
}
