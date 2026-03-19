package com.cheeseocean.im.common.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Kafka消息序列化器 - 将对象序列化为byte数组
 * 支持JSON格式序列化，兼容各种消息类型
 *
 * @author CheeseIM
 */
public class KafkaMessageSerializer implements Serializer<Object> {

    private static final Logger logger = LoggerFactory.getLogger(KafkaMessageSerializer.class);
    
    private ObjectMapper objectMapper;

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        // 初始化ObjectMapper
        this.objectMapper = new ObjectMapper();
        
        // 配置ObjectMapper
        objectMapper.findAndRegisterModules();
        
        logger.debug("KafkaMessageSerializer configured, isKey: {}", isKey);
    }

    @Override
    public byte[] serialize(String topic, Object data) {
        if (data == null) {
            logger.debug("Serializing null data for topic: {}", topic);
            return null;
        }

        try {
            // 如果已经是byte数组，直接返回
            if (data instanceof byte[]) {
                logger.debug("Data is already byte array for topic: {}, length: {}", topic, ((byte[]) data).length);
                return (byte[]) data;
            }

            // 如果是字符串，直接转换为byte数组
            if (data instanceof String) {
                byte[] bytes = ((String) data).getBytes(StandardCharsets.UTF_8);
                logger.debug("Serialized string to bytes for topic: {}, length: {}", topic, bytes.length);
                return bytes;
            }

            // 其他对象使用JSON序列化
            String json = objectMapper.writeValueAsString(data);
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            
            logger.debug("Serialized object to JSON bytes for topic: {}, class: {}, length: {}", 
                    topic, data.getClass().getSimpleName(), bytes.length);
            
            return bytes;

        } catch (Exception e) {
            String errorMsg = String.format("Failed to serialize data for topic: %s, class: %s", 
                    topic, data.getClass().getSimpleName());
            logger.error(errorMsg, e);
            throw new SerializationException(errorMsg, e);
        }
    }

    @Override
    public void close() {
        // 清理资源
        logger.debug("KafkaMessageSerializer closed");
    }
}
