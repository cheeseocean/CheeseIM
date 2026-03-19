package com.cheeseocean.im.common.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 泛型Kafka反序列化器 - 支持指定具体类型的反序列化
 * 
 * @param <T> 目标类型
 * @author CheeseIM
 */
public class GenericKafkaDeserializer<T> implements Deserializer<T> {

    private static final Logger logger = LoggerFactory.getLogger(GenericKafkaDeserializer.class);
    
    private ObjectMapper objectMapper;
    private Class<T> targetType;

    public GenericKafkaDeserializer() {
        this.objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
    }

    public GenericKafkaDeserializer(Class<T> targetType) {
        this();
        this.targetType = targetType;
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        // 从配置中获取目标类型
        if (targetType == null) {
            Object typeConfig = configs.get("value.deserializer.target.type");
            if (typeConfig instanceof String) {
                try {
                    @SuppressWarnings("unchecked")
                    Class<T> clazz = (Class<T>) Class.forName((String) typeConfig);
                    this.targetType = clazz;
                    logger.debug("Target type configured from config: {}", targetType.getName());
                } catch (ClassNotFoundException e) {
                    logger.error("Failed to load target type from config: {}", typeConfig, e);
                    throw new SerializationException("Invalid target type: " + typeConfig, e);
                }
            }
        }
        
        if (targetType == null) {
            throw new SerializationException("Target type must be specified");
        }
        
        logger.debug("GenericKafkaDeserializer configured, isKey: {}, targetType: {}", 
                isKey, targetType.getSimpleName());
    }

    @Override
    public T deserialize(String topic, byte[] data) {
        if (data == null) {
            logger.debug("Deserializing null data for topic: {}", topic);
            return null;
        }

        try {
            // 如果目标类型是byte数组，直接返回
            if (targetType == byte[].class) {
                @SuppressWarnings("unchecked")
                T result = (T) data;
                logger.debug("Returning raw bytes for topic: {}, length: {}", topic, data.length);
                return result;
            }

            // 转换为字符串
            String jsonString = new String(data, StandardCharsets.UTF_8);
            
            // 如果目标类型是String，直接返回
            if (targetType == String.class) {
                @SuppressWarnings("unchecked")
                T result = (T) jsonString;
                logger.debug("Deserialized to string for topic: {}, length: {}", topic, jsonString.length());
                return result;
            }

            // 其他类型使用JSON反序列化
            T result = objectMapper.readValue(jsonString, targetType);
            
            logger.debug("Deserialized JSON to object for topic: {}, targetType: {}, dataLength: {}", 
                    topic, targetType.getSimpleName(), data.length);
            
            return result;

        } catch (Exception e) {
            String errorMsg = String.format("Failed to deserialize data for topic: %s, targetType: %s, dataLength: %d", 
                    topic, targetType.getSimpleName(), data.length);
            logger.error(errorMsg, e);
            throw new SerializationException(errorMsg, e);
        }
    }

    @Override
    public void close() {
        logger.debug("GenericKafkaDeserializer closed");
    }

    /**
     * 设置目标类型
     */
    public void setTargetType(Class<T> targetType) {
        this.targetType = targetType;
    }

    /**
     * 获取目标类型
     */
    public Class<T> getTargetType() {
        return targetType;
    }
}
