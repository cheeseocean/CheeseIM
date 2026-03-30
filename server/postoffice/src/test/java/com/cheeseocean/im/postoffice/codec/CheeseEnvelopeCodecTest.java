package com.cheeseocean.im.postoffice.codec;

import com.cheeseocean.im.common.api.dto.message.ChatSendRequest;
import com.cheeseocean.im.common.api.protocol.ClientEnvelope;
import com.cheeseocean.im.common.api.protocol.ServerEnvelope;
import com.cheeseocean.im.common.core.enums.CommandType;
import com.cheeseocean.im.postoffice.client.ProtocolContractFixtures;
import io.netty.buffer.Unpooled;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheeseEnvelopeCodecTest {

    @Test
    void decoderShouldEmitClientEnvelopeDirectly() {
        EmbeddedChannel channel = new EmbeddedChannel(new TcpEnvelopeDecoder());

        assertTrue(channel.writeInbound(Unpooled.wrappedBuffer(ProtocolContractFixtures.tcpSendRequestBytes())));

        Object inbound = channel.readInbound();
        assertNotNull(inbound);
        ClientEnvelope envelope = assertInstanceOf(ClientEnvelope.class, inbound);
        assertEquals(CommandType.CHAT_SEND, envelope.getCommand());
        ChatSendRequest body = assertInstanceOf(ChatSendRequest.class, envelope.getBody());
        assertEquals(ProtocolContractFixtures.CLIENT_MSG_ID, body.getClientMsgId());
        assertEquals(ProtocolContractFixtures.PEER_USER_ID, body.getRecvId());
    }

    @Test
    void encoderShouldWriteTcpFrameFromServerEnvelopeDirectly() {
        EmbeddedChannel channel = new EmbeddedChannel(new TcpEnvelopeEncoder());

        ServerEnvelope envelope = ServerEnvelope.chatSend("op-send-1", Map.of(
                "serverMsgID", "server-1",
                "clientMsgID", "client-1",
                "sendTime", 1710000000000L
        ));

        assertTrue(channel.writeOutbound(envelope));

        ByteBuf outbound = channel.readOutbound();
        assertNotNull(outbound);
        byte[] bytes = new byte[outbound.readableBytes()];
        outbound.readBytes(bytes);

        ProtocolContractFixtures.RawTcpFrame frame = ProtocolContractFixtures.decodeTcpFrame(bytes);
        assertEquals(ProtocolContractFixtures.TCP_SEND_MSG_RESP, frame.msgType());
        assertEquals("op-send-1", frame.requestId());
        assertTrue(frame.data().contains("\"serverMsgID\":\"server-1\""));
    }
}
