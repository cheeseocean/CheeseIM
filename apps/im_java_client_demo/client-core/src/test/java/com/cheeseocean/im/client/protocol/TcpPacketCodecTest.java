package com.cheeseocean.im.client.protocol;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TcpPacketCodecTest {

    @Test
    void encodeAndDecodeShouldRoundTripAuthPacket() throws Exception {
        TcpPacket packet = new TcpPacket(
                TcpMessageTypes.TCP_AUTH_REQ,
                "op-auth-00000001",
                1710000000000L,
                "{\"token\":\"jwt-token\",\"userID\":\"user123\",\"platformID\":2}"
        );

        byte[] encoded = TcpPacketCodec.encode(packet);
        TcpPacket decoded = TcpPacketCodec.read(new ByteArrayInputStream(encoded));

        assertEquals(TcpMessageTypes.TCP_AUTH_REQ, decoded.msgType());
        assertEquals("op-auth-00000001", decoded.operationId());
        assertEquals(1710000000000L, decoded.timestamp());
        assertEquals("{\"token\":\"jwt-token\",\"userID\":\"user123\",\"platformID\":2}", decoded.data());
    }

    @Test
    void readPacketShouldHandleHeaderThenPayloadFromStream() throws Exception {
        TcpPacket packet = new TcpPacket(
                TcpMessageTypes.TCP_SEND_MSG_REQ,
                "send-00000000001",
                1710000000001L,
                "{\"clientMsgID\":\"client-123\",\"recvID\":\"userB\",\"content\":\"hello\",\"contentType\":101,\"sessionType\":1}"
        );

        byte[] encoded = TcpPacketCodec.encode(packet);
        ByteArrayInputStream stream = new ByteArrayInputStream(encoded);

        TcpPacket decoded = TcpPacketCodec.read(stream);

        assertEquals(TcpMessageTypes.TCP_SEND_MSG_REQ, decoded.msgType());
        assertEquals("send-00000000001", decoded.operationId());
        assertEquals(packet.data(), decoded.data());
    }

    @Test
    void operationIdShouldBeClampedToSixteenBytes() throws Exception {
        TcpPacket packet = new TcpPacket(
                TcpMessageTypes.TCP_HEARTBEAT_REQ,
                "operation-id-longer-than-sixteen",
                1710000000002L,
                "ping"
        );

        byte[] encoded = TcpPacketCodec.encode(packet);
        byte[] operationIdBytes = new byte[16];
        System.arraycopy(encoded, 8, operationIdBytes, 0, 16);

        assertEquals("operation-id-lon", new String(operationIdBytes, StandardCharsets.UTF_8));
    }
}
