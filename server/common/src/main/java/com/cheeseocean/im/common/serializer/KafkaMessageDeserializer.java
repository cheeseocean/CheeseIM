package com.cheeseocean.im.common.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Kafka消息反序列化器 - 将byte数组反序列化为对象
 * 支持JSON格式反序列化，兼容各种消息类型
 *
 * @author CheeseIM
 */
public class KafkaMessageDeserializer implements Deserializer<Object> {

    private static final Logger logger = LoggerFactory.getLogger(KafkaMessageDeserializer.class);
    
    private ObjectMapper objectMapper;
    private Class<?> targetType;

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        // 初始化ObjectMapper
        this.objectMapper = new ObjectMapper();
        
        // 配置ObjectMapper
        objectMapper.findAndRegisterModules();
        
        // 获取目标类型配置
        Object typeConfig = configs.get("value.deserializer.target.type");
        if (typeConfig instanceof String) {
            try {
                this.targetType = Class.forName((String) typeConfig);
                logger.debug("Target type configured: {}", targetType.getName());
            } catch (ClassNotFoundException e) {
                logger.warn("Failed to load target type: {}, will use String as default", typeConfig);
                this.targetType = String.class;
            }
        } else {
            this.targetType = String.class;
        }
        
        logger.debug("KafkaMessageDeserializer configured, isKey: {}, targetType: {}", 
                isKey, targetType.getSimpleName());
    }

    @Override
    public Object deserialize(String topic, byte[] data) {
        if (data == null) {
            logger.debug("Deserializing null data for topic: {}", topic);
            return null;
        }

        try {
            // 如果目标类型是byte数组，直接返回
            if (targetType == byte[].class) {
                logger.debug("Returning raw bytes for topic: {}, length: {}", topic, data.length);
                return data;
            }

            // 转换为字符串
            String jsonString = new String(data, StandardCharsets.UTF_8);
            
            // 如果目标类型是String，直接返回
            if (targetType == String.class) {
                logger.debug("Deserialized to string for topic: {}, length: {}", topic, jsonString.length());
                return jsonString;
            }

            // 其他类型使用JSON反序列化
            Object result = objectMapper.readValue(jsonString, targetType);
            
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
        // 清理资源
        logger.debug("KafkaMessageDeserializer closed");
    }
}
