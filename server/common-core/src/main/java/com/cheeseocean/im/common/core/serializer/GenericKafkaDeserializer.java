package com.cheeseocean.im.common.core.serializer;

import com.cheeseocean.im.common.core.util.ObjectMapperFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public class GenericKafkaDeserializer<T> implements Deserializer<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenericKafkaDeserializer.class);

    private final ObjectMapper objectMapper;
    private Class<T> targetType;

    public GenericKafkaDeserializer() {
        this.objectMapper = ObjectMapperFactory.createDefaultMapper();
    }

    public GenericKafkaDeserializer(Class<T> targetType) {
        this();
        this.targetType = targetType;
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        if (targetType == null) {
            Object typeConfig = configs.get("value.deserializer.target.type");
            if (typeConfig instanceof String typeName) {
                try {
                    @SuppressWarnings("unchecked")
                    Class<T> clazz = (Class<T>) Class.forName(typeName);
                    this.targetType = clazz;
                    LOGGER.debug("Target type configured from config: {}", targetType.getName());
                } catch (ClassNotFoundException e) {
                    LOGGER.error("Failed to load target type from config: {}", typeConfig, e);
                    throw new SerializationException("Invalid target type: " + typeConfig, e);
                }
            }
        }

        if (targetType == null) {
            throw new SerializationException("Target type must be specified");
        }

        LOGGER.debug("GenericKafkaDeserializer configured, isKey: {}, targetType: {}",
                isKey, targetType.getSimpleName());
    }

    @Override
    public T deserialize(String topic, byte[] data) {
        if (data == null) {
            LOGGER.debug("Deserializing null data for topic: {}", topic);
            return null;
        }

        try {
            if (targetType == byte[].class) {
                @SuppressWarnings("unchecked")
                T result = (T) data;
                LOGGER.debug("Returning raw bytes for topic: {}, length: {}", topic, data.length);
                return result;
            }

            String jsonString = new String(data, StandardCharsets.UTF_8);
            if (targetType == String.class) {
                @SuppressWarnings("unchecked")
                T result = (T) jsonString;
                LOGGER.debug("Deserialized to string for topic: {}, length: {}", topic, jsonString.length());
                return result;
            }

            T result = objectMapper.readValue(jsonString, targetType);
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
        LOGGER.debug("GenericKafkaDeserializer closed");
    }

    public void setTargetType(Class<T> targetType) {
        this.targetType = targetType;
    }

    public Class<T> getTargetType() {
        return targetType;
    }
}
