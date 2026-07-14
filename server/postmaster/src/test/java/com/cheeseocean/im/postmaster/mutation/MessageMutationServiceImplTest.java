package com.cheeseocean.im.postmaster.mutation;

import com.cheeseocean.im.common.api.dto.message.MessageMutationResult;
import com.cheeseocean.im.common.api.permission.ConversationPermissionDubboService;
import com.cheeseocean.im.common.core.auth.PermissionCheckResult;
import com.cheeseocean.im.postmaster.history.MessageIdMappingDoc;
import com.cheeseocean.im.postmaster.service.GroupMembershipFacade;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageMutationServiceImplTest {

    @Test
    void revokeShouldRejectMessageFromAnotherConversation() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        MessageIdMappingDoc mapping = mapping("s:owner:peer", "owner", System.currentTimeMillis());
        when(mongoTemplate.findOne(any(), eq(MessageIdMappingDoc.class))).thenReturn(mapping);

        MessageMutationServiceImpl service = service(mongoTemplate, PermissionCheckResult.allow());
        MessageMutationResult result = service.revoke("owner", "s:owner:other", "server-1", null);

        assertFalse(result.isSuccess());
        assertEquals("CONVERSATION_MISMATCH", result.getErrorCode());
    }

    @Test
    void revokeShouldRejectExpiredServerSendTime() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        MessageIdMappingDoc mapping = mapping("s:owner:peer", "owner", System.currentTimeMillis() - 121_000L);
        when(mongoTemplate.findOne(any(), eq(MessageIdMappingDoc.class))).thenReturn(mapping);

        MessageMutationServiceImpl service = service(mongoTemplate, PermissionCheckResult.allow());
        MessageMutationResult result = service.revoke("owner", "s:owner:peer", "server-1", null);

        assertFalse(result.isSuccess());
        assertEquals("REVOKE_WINDOW_EXPIRED", result.getErrorCode());
    }

    @Test
    void revokeShouldReturnExistingOverlayForIdempotentRetry() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        MessageIdMappingDoc mapping = mapping("s:owner:peer", "owner", System.currentTimeMillis());
        when(mongoTemplate.findOne(any(), eq(MessageIdMappingDoc.class))).thenReturn(mapping);
        MessageMutationDoc existing = mutation("server-1:REVOKED", "s:owner:peer", "server-1");
        when(mongoTemplate.findById("server-1:REVOKED", MessageMutationDoc.class)).thenReturn(existing);

        MessageMutationServiceImpl service = service(mongoTemplate, PermissionCheckResult.allow());
        MessageMutationResult result = service.revoke("owner", "s:owner:peer", "server-1", "again");

        assertTrue(result.isSuccess());
        assertEquals("server-1:REVOKED", result.getMutationId());
        assertEquals(existing.getMutationVersion().longValue(), result.getMutationVersion());
        verify(mongoTemplate).findById("server-1:REVOKED", MessageMutationDoc.class);
    }

    private static MessageMutationServiceImpl service(MongoTemplate mongoTemplate, PermissionCheckResult permission) {
        MessageMutationServiceImpl service = new MessageMutationServiceImpl(
                mongoTemplate, 120L, mock(GroupMembershipFacade.class));
        ConversationPermissionDubboService permissionService = mock(ConversationPermissionDubboService.class);
        when(permissionService.check(any())).thenReturn(permission);
        ReflectionTestUtils.setField(service, "conversationPermissionDubboService", permissionService);
        return service;
    }

    private static MessageIdMappingDoc mapping(String conversationId, String senderId, long sendTime) {
        MessageIdMappingDoc mapping = new MessageIdMappingDoc();
        mapping.setConversationId(conversationId);
        mapping.setServerMsgId("server-1");
        mapping.setSenderId(senderId);
        mapping.setSendTime(sendTime);
        return mapping;
    }

    private static MessageMutationDoc mutation(String id, String conversationId, String serverMsgId) {
        MessageMutationDoc mutation = new MessageMutationDoc();
        mutation.setId(id);
        mutation.setConversationId(conversationId);
        mutation.setServerMsgId(serverMsgId);
        mutation.setOperatorUserId("owner");
        mutation.setOperatorName("owner");
        mutation.setTargetSenderId("owner");
        mutation.setTargetSenderName("owner");
        mutation.setMutationVersion(123L);
        mutation.setCreatedAt(Instant.ofEpochMilli(456L));
        return mutation;
    }
}
