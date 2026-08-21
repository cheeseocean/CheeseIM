package com.cheeseocean.im.common.api.protocol;

import com.cheeseocean.im.common.api.enums.CommandType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ServerEnvelopeTest {

    @Test
    void shouldNormalizeJdkImmutableCollectionsForStrictRpcSerialization() {
        ServerEnvelope envelope = ServerEnvelope.of(CommandType.CHAT_READ, "read-1", Map.of(
                "conversationId", "s:user-1:user-2",
                "targets", List.of("user-1", "user-2")
        ));

        Map<?, ?> body = assertInstanceOf(LinkedHashMap.class, envelope.getBody());
        assertEquals("s:user-1:user-2", body.get("conversationId"));
        assertInstanceOf(ArrayList.class, body.get("targets"));
    }
}
