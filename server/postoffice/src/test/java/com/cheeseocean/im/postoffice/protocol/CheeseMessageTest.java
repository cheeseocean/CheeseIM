package com.cheeseocean.im.postoffice.protocol;

import com.cheeseocean.im.common.api.dto.dispatch.DispatchPayload;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
