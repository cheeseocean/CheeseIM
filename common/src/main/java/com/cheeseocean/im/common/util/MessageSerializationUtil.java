package com.cheeseocean.im.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * 消息序列化工具类 - 提供便捷的序列化/反序列化方法
 * 
 * @author CheeseIM
 */
public class MessageSerializationUtil {

    private static final Logger logger = LoggerFactory.getLogger(MessageSerializationUtil.class);
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    static {
        // 配置ObjectMapper
        objectMapper.findAndRegisterModules();
    }

    /**
     * 将对象序列化为byte数组
     * 
     * @param object 要序列化的对象
     * @return byte数组
     */
    public static byte[] serialize(Object object) {
        if (object == null) {
            return null;
        }

        try {
            // 如果已经是byte数组，直接返回
            if (object instanceof byte[]) {
                return (byte[]) object;
            }

            // 如果是字符串，直接转换
            if (object instanceof String) {
                return ((String) object).getBytes(StandardCharsets.UTF_8);
            }

            // 其他对象使用JSON序列化
            String json = objectMapper.writeValueAsString(object);
            return json.getBytes(StandardCharsets.UTF_8);

        } catch (Exception e) {
            logger.error("Failed to serialize object: {}", object.getClass().getSimpleName(), e);
            throw new RuntimeException("Serialization failed", e);
        }
    }

    /**
     * 将byte数组反序列化为字符串
     * 
     * @param data byte数组
     * @return 字符串
     */
    public static String deserializeToString(byte[] data) {
        if (data == null) {
            return null;
        }

        try {
            return new String(data, StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.error("Failed to deserialize bytes to string, length: {}", data.length, e);
            throw new RuntimeException("Deserialization to string failed", e);
        }
    }

    /**
     * 将byte数组反序列化为指定类型的对象
     * 
     * @param data byte数组
     * @param targetType 目标类型
     * @param <T> 泛型类型
     * @return 反序列化后的对象
     */
    public static <T> T deserialize(byte[] data, Class<T> targetType) {
        if (data == null) {
            return null;
        }

        try {
            // 如果目标类型是byte数组，直接返回
            if (targetType == byte[].class) {
                @SuppressWarnings("unchecked")
                T result = (T) data;
                return result;
            }

            // 转换为字符串
            String jsonString = new String(data, StandardCharsets.UTF_8);

            // 如果目标类型是String，直接返回
            if (targetType == String.class) {
                @SuppressWarnings("unchecked")
                T result = (T) jsonString;
                return result;
            }

            // 其他类型使用JSON反序列化
            return objectMapper.readValue(jsonString, targetType);

        } catch (Exception e) {
            logger.error("Failed to deserialize bytes to {}, length: {}", 
                    targetType.getSimpleName(), data.length, e);
            throw new RuntimeException("Deserialization failed", e);
        }
    }

    /**
     * 将JSON字符串反序列化为指定类型的对象
     * 
     * @param json JSON字符串
     * @param targetType 目标类型
     * @param <T> 泛型类型
     * @return 反序列化后的对象
     */
    public static <T> T deserializeFromJson(String json, Class<T> targetType) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }

        try {
            return objectMapper.readValue(json, targetType);
        } catch (Exception e) {
            logger.error("Failed to deserialize JSON to {}, json: {}", 
                    targetType.getSimpleName(), json, e);
            throw new RuntimeException("JSON deserialization failed", e);
        }
    }

    /**
     * 将对象序列化为JSON字符串
     * 
     * @param object 要序列化的对象
     * @return JSON字符串
     */
    public static String serializeToJson(Object object) {
        if (object == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            logger.error("Failed to serialize object to JSON: {}", 
                    object.getClass().getSimpleName(), e);
            throw new RuntimeException("JSON serialization failed", e);
        }
    }

    /**
     * 检查byte数组是否为有效的JSON格式
     * 
     * @param data byte数组
     * @return 是否为有效JSON
     */
    public static boolean isValidJson(byte[] data) {
        if (data == null || data.length == 0) {
            return false;
        }

        try {
            String jsonString = new String(data, StandardCharsets.UTF_8);
            objectMapper.readTree(jsonString);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查字符串是否为有效的JSON格式
     * 
     * @param json JSON字符串
     * @return 是否为有效JSON
     */
    public static boolean isValidJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return false;
        }

        try {
            objectMapper.readTree(json);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取ObjectMapper实例（用于高级操作）
     * 
     * @return ObjectMapper实例
     */
    public static ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}
