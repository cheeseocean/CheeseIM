package com.cheeseocean.im.common.core.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public class KafkaMessageSerializer implements Serializer<Object> {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaMessageSerializer.class);

    private ObjectMapper objectMapper;

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        this.objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        LOGGER.debug("KafkaMessageSerializer configured, isKey: {}", isKey);
    }

    @Override
    public byte[] serialize(String topic, Object data) {
        if (data == null) {
            LOGGER.debug("Serializing null data for topic: {}", topic);
            return null;
        }

        try {
            if (data instanceof byte[] bytes) {
                LOGGER.debug("Data is already byte array for topic: {}, length: {}", topic, bytes.length);
                return bytes;
            }

            if (data instanceof String text) {
                byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
                LOGGER.debug("Serialized string to bytes for topic: {}, length: {}", topic, bytes.length);
                return bytes;
            }

            String json = objectMapper.writeValueAsString(data);
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            LOGGER.debug("Serialized object to JSON bytes for topic: {}, class: {}, length: {}",
                    topic, data.getClass().getSimpleName(), bytes.length);
            return bytes;
        } catch (Exception e) {
            String errorMsg = String.format("Failed to serialize data for topic: %s, class: %s",
                    topic, data.getClass().getSimpleName());
            LOGGER.error(errorMsg, e);
            throw new SerializationException(errorMsg, e);
        }
    }

    @Override
    public void close() {
        LOGGER.debug("KafkaMessageSerializer closed");
    }
}
