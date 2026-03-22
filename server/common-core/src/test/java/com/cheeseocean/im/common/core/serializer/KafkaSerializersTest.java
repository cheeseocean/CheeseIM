package com.cheeseocean.im.common.core.serializer;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class KafkaSerializersTest {

    @Test
    void serializerShouldPassThroughStringsAndBytes() {
        KafkaMessageSerializer serializer = new KafkaMessageSerializer();
        serializer.configure(Map.of(), false);

        byte[] stringBytes = serializer.serialize("topic-a", "hello");
        byte[] rawBytes = serializer.serialize("topic-a", "world".getBytes(StandardCharsets.UTF_8));

        assertEquals("hello", new String(stringBytes, StandardCharsets.UTF_8));
        assertArrayEquals("world".getBytes(StandardCharsets.UTF_8), rawBytes);
    }

    @Test
    void genericDeserializerShouldRestoreConfiguredType() {
        GenericKafkaDeserializer<TestPayload> deserializer = new GenericKafkaDeserializer<>();
        deserializer.configure(Map.of("value.deserializer.target.type", TestPayload.class.getName()), false);

        TestPayload payload = deserializer.deserialize("topic-a", "{\"name\":\"alice\",\"count\":3}".getBytes(StandardCharsets.UTF_8));

        assertEquals("alice", payload.getName());
        assertEquals(3, payload.getCount());
    }

    @Test
    void messageDeserializerShouldReturnStringByDefault() {
        KafkaMessageDeserializer deserializer = new KafkaMessageDeserializer();
        deserializer.configure(Map.of(), false);

        Object payload = deserializer.deserialize("topic-a", "hello".getBytes(StandardCharsets.UTF_8));

        assertEquals("hello", payload);
    }

    public static class TestPayload {
        private String name;
        private int count;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }
}
