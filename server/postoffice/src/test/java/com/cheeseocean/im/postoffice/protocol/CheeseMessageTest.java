package com.cheeseocean.im.postoffice.protocol;

import com.cheeseocean.im.common.api.dto.dispatch.DispatchPayload;
import com.cheeseocean.im.common.api.dto.message.ChatSendRequest;
import com.cheeseocean.im.common.api.protocol.ClientEnvelope;
import com.cheeseocean.im.common.api.protocol.ServerEnvelope;
import com.cheeseocean.im.common.core.enums.CommandType;
import com.cheeseocean.im.postoffice.client.ProtocolContractFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheeseMessageTest {

    @Test
    void toWsMessageShouldTranslateTcpAuthTypeToWsAuthType() {
        CheeseMessage message = new CheeseMessage(
                CheeseMessageType.TCP_AUTH_REQ,
                "op-auth-1",
                "{\"ticket\":\"demo\"}"
        );

        WSMessage wsMessage = message.toWSMessage();

        assertEquals(WSMessageType.WS_AUTH_REQ, wsMessage.getMsgType());
        assertEquals("{\"ticket\":\"demo\"}", wsMessage.getData());
    }

    @Test
    void toWsMessageShouldTranslateTcpSendTypeToWsSendType() {
        CheeseMessage message = new CheeseMessage(
                CheeseMessageType.TCP_SEND_MSG_REQ,
                "op-send-1",
                "{\"content\":\"hello\"}"
        );

        WSMessage wsMessage = message.toWSMessage();

        assertEquals(WSMessageType.WS_SEND_MSG_REQ, wsMessage.getMsgType());
    }

    @Test
    void toWsMessageShouldTranslateTcpReadReceiptToWsReadNotify() {
        CheeseMessage message = new CheeseMessage(
                CheeseMessageType.TCP_MSG_READ_RECEIPT,
                "op-read-1",
                "{\"receiptType\":\"READ_CURSOR\",\"seq\":19}"
        );

        WSMessage wsMessage = message.toWSMessage();

        assertEquals(WSMessageType.WS_MSG_READ_NOTIFY, wsMessage.getMsgType());
    }

    @Test
    void toClientEnvelopeShouldTranslateTcpSendMessageToChatSendRequest() throws Exception {
        CheeseMessage message = new CheeseMessage(
                CheeseMessageType.TCP_SEND_MSG_REQ,
                "op-chat-1",
                ProtocolContractFixtures.tcpSendRequestJson()
        );

        ClientEnvelope envelope = message.toClientEnvelope();

        assertEquals(CommandType.CHAT_SEND, envelope.getCommand());
        assertEquals("op-chat-1", envelope.getRequestId());
        ChatSendRequest body = assertInstanceOf(ChatSendRequest.class, envelope.getBody());
        assertEquals(ProtocolContractFixtures.PEER_USER_ID, body.getRecvId());
        assertEquals(ProtocolContractFixtures.CLIENT_MSG_ID, body.getClientMsgId());
        assertEquals(101, body.getContentType());

        String serialized = new ObjectMapper().writeValueAsString(body);
        assertTrue(serialized.contains("\"clientMsgID\":\"" + ProtocolContractFixtures.CLIENT_MSG_ID + "\""));
        assertTrue(serialized.contains("\"recvID\":\"" + ProtocolContractFixtures.PEER_USER_ID + "\""));
        assertTrue(serialized.contains("\"content\":\"Hello World!\""));
        assertTrue(serialized.contains("\"sessionType\":1"));
    }

    @Test
    void toClientEnvelopeShouldNotSupportLegacyTcpReadReceipt() {
        CheeseMessage message = new CheeseMessage(
                CheeseMessageType.TCP_MSG_READ_RECEIPT,
                "op-read-2",
                "{\"receiptType\":\"DELIVERED\",\"conversationId\":\"single:user-a:user-b\",\"serverMsgId\":\"msg-88\",\"receiptTime\":1710000000001,\"seq\":19}"
        );

        ClientEnvelope envelope = message.toClientEnvelope();

        assertNull(envelope.getCommand());
        assertEquals("op-read-2", envelope.getRequestId());
        assertTrue(envelope.getBody() instanceof String);
    }

    @Test
    void toClientEnvelopeShouldTranslateWsSendMessageToChatSendRequest() throws Exception {
        WSMessage wsMessage = ProtocolContractFixtures.wsSendRequest();

        ClientEnvelope envelope = wsMessage.toClientEnvelope();

        assertEquals(CommandType.CHAT_SEND, envelope.getCommand());
        assertEquals(ProtocolContractFixtures.SEND_OPERATION_ID, envelope.getRequestId());
        ChatSendRequest body = assertInstanceOf(ChatSendRequest.class, envelope.getBody());
        assertEquals(ProtocolContractFixtures.PEER_USER_ID, body.getRecvId());
        assertEquals(ProtocolContractFixtures.CLIENT_MSG_ID, body.getClientMsgId());
        assertEquals(101, body.getContentType());

        String serialized = new ObjectMapper().writeValueAsString(body);
        assertTrue(serialized.contains("\"clientMsgID\":\"" + ProtocolContractFixtures.CLIENT_MSG_ID + "\""));
        assertTrue(serialized.contains("\"recvID\":\"" + ProtocolContractFixtures.PEER_USER_ID + "\""));
        assertTrue(serialized.contains("\"content\":\"Hello World!\""));
        assertTrue(serialized.contains("\"sessionType\":1"));
    }

    @Test
    void toClientEnvelopeShouldNotSupportLegacyWsReadReceipt() {
        WSMessage wsMessage = new WSMessage(
                WSMessageType.WS_MSG_READ_NOTIFY,
                "op-read-3",
                java.util.Map.of(
                        "receiptType", "RECEIVED",
                        "conversationId", "single:user-a:user-b",
                        "serverMsgId", "msg-99",
                        "receiptTime", 1710000000002L,
                        "seq", 21L
                )
        );

        ClientEnvelope envelope = wsMessage.toClientEnvelope();

        assertNull(envelope.getCommand());
        assertEquals("op-read-3", envelope.getRequestId());
        assertTrue(envelope.getBody() instanceof java.util.Map);
    }

    @Test
    void fromWsMessageShouldSerializeObjectPayloadAsJson() {
        DispatchPayload payload = new DispatchPayload();
        payload.setServerMsgId("msg-1");
        payload.setClientMsgId("client-1");
        payload.setConversationId("single:userA:userB");
        payload.setSeq(7L);
        payload.setContent("hello");
        payload.setContentType(101);
        payload.setSendTime(1710000000002L);

        WSMessage wsMessage = new WSMessage();
        wsMessage.setMsgType(WSMessageType.WS_RECV_MSG_NOTIFY);
        wsMessage.setOperationID("op-recv-1");
        wsMessage.setSendTime(1710000000002L);
        wsMessage.setData(payload);

        CheeseMessage cheeseMessage = CheeseMessage.fromWSMessage(wsMessage);

        assertEquals(CheeseMessageType.TCP_RECV_MSG_NOTIFY, cheeseMessage.getMsgType());
        assertTrue(cheeseMessage.getData().contains("\"serverMsgId\":\"msg-1\""));
        assertTrue(cheeseMessage.getData().contains("\"seq\":7"));
        assertTrue(cheeseMessage.getData().contains("\"content\":\"hello\""));
    }

    @Test
    void fromWsMessageShouldTranslateWsRevokeNotifyToTcpRevokeNotify() {
        WSMessage wsMessage = new WSMessage();
        wsMessage.setMsgType(WSMessageType.WS_MSG_REVOKE_NOTIFY);
        wsMessage.setOperationID("op-revoke-1");
        wsMessage.setSendTime(1710000000002L);
        wsMessage.setData("{\"targetServerMsgId\":\"msg-1\"}");

        CheeseMessage cheeseMessage = CheeseMessage.fromWSMessage(wsMessage);

        assertEquals(CheeseMessageType.TCP_REVOKE_MSG_NOTIFY, cheeseMessage.getMsgType());
        assertTrue(cheeseMessage.getData().contains("\"targetServerMsgId\":\"msg-1\""));
    }

    @Test
    void fromServerEnvelopeShouldTranslateForceLogoutToTransportSpecificFrames() {
        ServerEnvelope envelope = ServerEnvelope.forceLogout("system", "duplicate login");

        WSMessage wsMessage = WSMessage.fromServerEnvelope(envelope);
        CheeseMessage cheeseMessage = CheeseMessage.fromServerEnvelope(envelope);

        assertEquals(CommandType.FORCE_LOGOUT, wsMessage.toServerEnvelope().getCommand());
        assertEquals(WSMessageType.WS_FORCE_LOGOUT_NOTIFY, wsMessage.getMsgType());
        assertEquals(CheeseMessageType.TCP_FORCE_LOGOUT_NOTIFY, cheeseMessage.getMsgType());
        assertEquals(CommandType.FORCE_LOGOUT, cheeseMessage.toServerEnvelope().getCommand());
        assertTrue(cheeseMessage.getData().contains("\"reason\":\"duplicate login\""));
    }
}
