package com.cheeseocean.im.common.core.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public class KafkaMessageDeserializer implements Deserializer<Object> {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaMessageDeserializer.class);

    private ObjectMapper objectMapper;
    private Class<?> targetType;

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        this.objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        Object typeConfig = configs.get("value.deserializer.target.type");
        if (typeConfig instanceof String typeName) {
            try {
                this.targetType = Class.forName(typeName);
                LOGGER.debug("Target type configured: {}", targetType.getName());
            } catch (ClassNotFoundException e) {
                LOGGER.warn("Failed to load target type: {}, will use String as default", typeConfig);
                this.targetType = String.class;
            }
        } else {
            this.targetType = String.class;
        }

        LOGGER.debug("KafkaMessageDeserializer configured, isKey: {}, targetType: {}",
                isKey, targetType.getSimpleName());
    }

    @Override
    public Object deserialize(String topic, byte[] data) {
        if (data == null) {
            LOGGER.debug("Deserializing null data for topic: {}", topic);
            return null;
        }

        try {
            if (targetType == byte[].class) {
                LOGGER.debug("Returning raw bytes for topic: {}, length: {}", topic, data.length);
                return data;
            }

            String jsonString = new String(data, StandardCharsets.UTF_8);
            if (targetType == String.class) {
                LOGGER.debug("Deserialized to string for topic: {}, length: {}", topic, jsonString.length());
                return jsonString;
            }

            Object result = objectMapper.readValue(jsonString, targetType);
            LOGGER.debug("Deserialized JSON to object for topic: {}, targetType: {}, dataLength: {}",
                    topic, targetType.getSimpleName(), data.length);
            return result;
        } catch (Exception e) {
            String errorMsg = String.format("Failed to deserialize data for topic: %s, targetType: %s, dataLength: %d",
                    topic, targetType.getSimpleName(), data.length);
            LOGGER.error(errorMsg, e);
            throw new SerializationException(errorMsg, e);
        }
    }

    @Override
    public void close() {
        LOGGER.debug("KafkaMessageDeserializer closed");
    }
}
