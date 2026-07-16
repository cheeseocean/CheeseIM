package com.cheeseocean.im.common.api.protocol;

import com.cheeseocean.im.common.api.protocol.proto.ProtoChatSendAcceptedState;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProtoEnvelopeMapperDeliveryTest {
    @Test
    void chatSendAckShouldExplicitlyMeanBrokerAccepted() {
        var proto = ProtoEnvelopeMapper.toProto(ServerEnvelope.chatSendAck("op-1", Map.of(
                "serverMsgID", "m1", "clientMsgID", "c1", "sendTime", 100L, "acceptedAt", 123L))).getChatSendAck();

        assertEquals(123L, proto.getAcceptedAt());
        assertEquals(ProtoChatSendAcceptedState.CHAT_SEND_BROKER_ACCEPTED, proto.getAcceptedState());
    }
}
