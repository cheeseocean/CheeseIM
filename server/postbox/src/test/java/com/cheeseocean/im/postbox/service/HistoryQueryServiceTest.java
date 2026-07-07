package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.core.auth.PermissionCheckResult;
import com.cheeseocean.im.common.api.session.SessionPrincipal;
import com.cheeseocean.im.common.api.enums.ContentType;
import com.cheeseocean.im.common.api.enums.MessagePreviewType;
import com.cheeseocean.im.common.api.enums.SessionStatus;
import com.cheeseocean.im.postbox.model.HistoryMessage;
import com.cheeseocean.im.postbox.history.MessageBlockDoc;
import com.cheeseocean.im.postbox.history.MessageSlot;
import org.apache.dubbo.rpc.RpcException;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HistoryQueryServiceTest {

    @Test
    void getConversationMessagesShouldReadDescendingMessagesFromBlockHistory() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        when(mongoTemplate.findOne(any(), org.mockito.ArgumentMatchers.eq(MessageBlockDoc.class)))
                .thenReturn(block(1L, messages()));
        when(mongoTemplate.find(any(), org.mockito.ArgumentMatchers.eq(MessageBlockDoc.class)))
                .thenReturn(List.of(block(1L, messages(
                        slot(101L, "s-101", "c-101", "userA", "userB", "hello 101"),
                        slot(102L, "s-102", "c-102", "userA", "userB", "hello 102")
                ))));

        var permissionService = mock(com.cheeseocean.im.common.api.permission.ConversationPermissionDubboService.class);
        when(permissionService.check(any())).thenReturn(PermissionCheckResult.allow());

        HistoryQueryService service = new HistoryQueryService(mongoTemplate, new MessagePreviewResolver());
        org.springframework.test.util.ReflectionTestUtils.setField(service, "conversationPermissionDubboService", permissionService);

        List<HistoryMessage> messages = service.getConversationMessages(session("userB"), "single:userA:userB", 2);

        assertEquals(2, messages.size());
        assertEquals(102L, messages.get(0).getSequence());
        assertEquals("s-102", messages.get(0).getServerMsgId());
        assertEquals(101L, messages.get(1).getSequence());
    }

    @Test
    void getConversationMessagesShouldRenderReadableContentForSpecialMessageTypes() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        when(mongoTemplate.findOne(any(), org.mockito.ArgumentMatchers.eq(MessageBlockDoc.class)))
                .thenReturn(block(1L, messages()));
        when(mongoTemplate.find(any(), org.mockito.ArgumentMatchers.eq(MessageBlockDoc.class)))
                .thenReturn(List.of(block(1L, messages(
                        slot(103L, "s-103", "c-103", "userA", "userB", "raw-system", ContentType.SYSTEM_NOTIFY.getCode()),
                        slot(102L, "s-102", "c-102", "userA", "userB", "raw-revoke", ContentType.REVOKE_NOTIFY.getCode()),
                        slot(101L, "s-101", "c-101", "userA", "userB", "raw-read", ContentType.READ_RECEIPT.getCode())
                ))));

        var permissionService = mock(com.cheeseocean.im.common.api.permission.ConversationPermissionDubboService.class);
        when(permissionService.check(any())).thenReturn(PermissionCheckResult.allow());

        HistoryQueryService service = new HistoryQueryService(mongoTemplate, new MessagePreviewResolver());
        org.springframework.test.util.ReflectionTestUtils.setField(service, "conversationPermissionDubboService", permissionService);

        List<HistoryMessage> messages = service.getConversationMessages(session("userB"), "single:userA:userB", 3);

        assertEquals("系统通知", messages.get(0).getContent());
        assertEquals(MessagePreviewType.SYSTEM, messages.get(0).getPreviewType());
        assertEquals("你撤回了一条消息", messages.get(1).getContent());
        assertEquals(MessagePreviewType.REVOKE, messages.get(1).getPreviewType());
        assertEquals("[已读回执]", messages.get(2).getContent());
        assertEquals(MessagePreviewType.READ_RECEIPT, messages.get(2).getPreviewType());
    }

    @Test
    void getConversationMessagesShouldDenyWhenPermissionProviderIsUnavailableWithoutCache() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        when(mongoTemplate.find(any(), org.mockito.ArgumentMatchers.eq(MessageBlockDoc.class)))
                .thenReturn(List.of(block(1L, messages(
                        slot(101L, "s-101", "c-101", "userA", "userB", "hello 101")
                ))));

        var permissionService = mock(com.cheeseocean.im.common.api.permission.ConversationPermissionDubboService.class);
        when(permissionService.check(any())).thenThrow(new RpcException("no provider"));

        HistoryQueryService service = new HistoryQueryService(mongoTemplate, new MessagePreviewResolver());
        org.springframework.test.util.ReflectionTestUtils.setField(service, "conversationPermissionDubboService", permissionService);

        List<HistoryMessage> messages = service.getConversationMessages(session("userB"), "single:userA:userB", 1);

        assertEquals(0, messages.size());
    }

    @Test
    void getConversationMessagesShouldUseShortPermissionCacheWhenProviderFailsAfterSuccess() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        when(mongoTemplate.findOne(any(), org.mockito.ArgumentMatchers.eq(MessageBlockDoc.class)))
                .thenReturn(block(1L, messages()));
        when(mongoTemplate.find(any(), org.mockito.ArgumentMatchers.eq(MessageBlockDoc.class)))
                .thenReturn(List.of(block(1L, messages(
                        slot(101L, "s-101", "c-101", "userA", "userB", "hello 101")
                ))));

        var permissionService = mock(com.cheeseocean.im.common.api.permission.ConversationPermissionDubboService.class);
        when(permissionService.check(any()))
                .thenReturn(PermissionCheckResult.allow())
                .thenThrow(new RpcException("no provider"));

        HistoryQueryService service = new HistoryQueryService(mongoTemplate, new MessagePreviewResolver());
        org.springframework.test.util.ReflectionTestUtils.setField(service, "conversationPermissionDubboService", permissionService);

        service.getConversationMessages(session("userB"), "single:userA:userB", 1);
        List<HistoryMessage> messages = service.getConversationMessages(session("userB"), "single:userA:userB", 1);

        assertEquals(1, messages.size());
        assertEquals("s-101", messages.get(0).getServerMsgId());
    }

    private static SessionPrincipal session(String userId) {
        SessionPrincipal session = new SessionPrincipal();
        session.setUserId(userId);
        session.setTenantId("tenant_01");
        session.setSessionId("sess_01");
        session.setDeviceId("dev_01");
        session.setStatus(SessionStatus.ACTIVE);
        return session;
    }

    private static MessageBlockDoc block(long blockNo, List<MessageSlot> messages) {
        MessageBlockDoc block = new MessageBlockDoc();
        block.setId("single:userA:userB:" + blockNo);
        block.setConversationId("single:userA:userB");
        block.setBlockNo(blockNo);
        block.setMessages(messages);
        return block;
    }

    private static List<MessageSlot> messages(MessageSlot... slots) {
        return List.of(slots);
    }

    private static MessageSlot slot(long seq,
                                    String serverMsgId,
                                    String clientMsgId,
                                    String senderId,
                                    String recvId,
                                    String content) {
        return slot(seq, serverMsgId, clientMsgId, senderId, recvId, content, 101);
    }

    private static MessageSlot slot(long seq,
                                    String serverMsgId,
                                    String clientMsgId,
                                    String senderId,
                                    String recvId,
                                    String content,
                                    int contentType) {
        MessageSlot slot = new MessageSlot();
        slot.setSeq(seq);
        slot.setServerMsgId(serverMsgId);
        slot.setClientMsgId(clientMsgId);
        slot.setSenderId(senderId);
        slot.setRecvId(recvId);
        slot.setContent(content);
        slot.setContentType(contentType);
        slot.setSendTime(System.currentTimeMillis());
        return slot;
    }
}
