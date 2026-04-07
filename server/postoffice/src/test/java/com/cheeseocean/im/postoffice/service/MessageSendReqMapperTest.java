package com.cheeseocean.im.postoffice.service;

import com.cheeseocean.im.common.api.dto.message.SendMessageReq;
import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.enums.ConnectionState;
import com.cheeseocean.im.postoffice.connection.ConnectionContext;
import com.cheeseocean.im.postoffice.connection.UserConnection;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageSendReqMapperTest {

    @Test
    void mapsDirectMessageUsingAuthenticatedConnectionContext() {
        MessageSendReqMapper mapper = new MessageSendReqMapper();
        UserConnection connection = authenticatedConnection();
        Message message = new Message();
        message.setClientMsgId("cmsg-1");
        message.setReceiverId("u200");
        message.setContent("hello");
        message.setContentType(101);
        message.setSessionType(1);
        message.setSendTime(123L);
        message.setOptions(Map.of("needHistory", true, "needOnlinePush", true));
        message.setAttachedInfo("{\"attachmentId\":\"att-1\",\"downloadUrl\":\"https://cdn.example.com/a\"}");

        SendMessageReq req = mapper.map(message, connection, "op-1");

        assertEquals("op-1", req.getRequestId());
        assertEquals("u100", req.getSenderId());
        assertEquals("u200", req.getRecvId());
        assertEquals(101, req.getContentType());
        assertTrue(req.getOptions().isNeedHistory());
        assertTrue(req.getOptions().isNeedOnlinePush());
        assertEquals("{\"attachmentId\":\"att-1\",\"downloadUrl\":\"https://cdn.example.com/a\"}", req.getExt().get("attachedInfo"));
    }

    @Test
    void mapsGroupMessageAndFallsBackToPlatformAsDeviceIdHint() {
        MessageSendReqMapper mapper = new MessageSendReqMapper();
        UserConnection connection = new UserConnection();
        connection.setUserID("u100");
        connection.setPlatformType(5);

        Message message = new Message();
        message.setClientMsgId("cmsg-2");
        message.setGroupId("g1");
        message.setContent("team");
        message.setContentType(101);
        message.setSessionType(2);

        SendMessageReq req = mapper.map(message, connection, "op-2");

        assertEquals("g1", req.getGroupId());
        assertEquals(2, req.getSessionType());
        assertEquals("cmsg-2", req.getClientMsgId());
    }

    private static UserConnection authenticatedConnection() {
        UserConnection connection = new UserConnection();
        connection.setUserID("u100");
        connection.setPlatformType(5);
        ConnectionContext context = new ConnectionContext();
        context.setUserId("u100");
        context.setDeviceId("device-1");
        context.setPlatformCode(5);
        context.setState(ConnectionState.AUTHENTICATED);
        connection.setContext(context);
        return connection;
    }
}
