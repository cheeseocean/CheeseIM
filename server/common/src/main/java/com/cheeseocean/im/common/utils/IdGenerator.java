package com.cheeseocean.im.common.utils;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ID生成器工具类
 * 
 * @author CheeseIM
 */
public class IdGenerator {
    
    private static final AtomicLong sequence = new AtomicLong(0);
    
    /**
     * 生成UUID
     * 
     * @return UUID字符串
     */
    public static String generateUUID() {
        return UUID.randomUUID().toString().replace("-", "");
    }
    
    /**
     * 生成消息ID
     * 
     * @return 消息ID
     */
    public static String generateMsgId() {
        return "msg_" + System.currentTimeMillis() + "_" + sequence.incrementAndGet();
    }
    
    /**
     * 生成操作ID
     * 
     * @return 操作ID
     */
    public static String generateOperationId() {
        return "op_" + System.currentTimeMillis() + "_" + sequence.incrementAndGet();
    }
    
    /**
     * 生成序列号
     * 
     * @return 序列号
     */
    public static Long generateSeq() {
        return sequence.incrementAndGet();
    }
}
