package com.cheeseocean.im.postoffice.handler;

import com.cheeseocean.im.postoffice.protocol.WSMessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息处理器工厂
 * 负责管理和分发各种消息类型的处理器
 * 
 * @author CheeseIM
 */
@Component
public class MessageHandlerFactory {
    
    private static final Logger logger = LoggerFactory.getLogger(MessageHandlerFactory.class);
    
    @Autowired
    private List<MessageHandler> messageHandlers;
    
    /**
     * 消息类型 -> 处理器映射
     */
    private final Map<Integer, MessageHandler> handlerMap = new HashMap<>();
    
    /**
     * 初始化处理器映射
     */
    @PostConstruct
    public void init() {
        for (MessageHandler handler : messageHandlers) {
            int messageType = handler.getSupportedMessageType();
            handlerMap.put(messageType, handler);
            
            logger.info("Registered message handler: {} -> {}", 
                       WSMessageType.getMessageTypeDesc(messageType), 
                       handler.getClass().getSimpleName());
        }
        
        logger.info("MessageHandlerFactory initialized with {} handlers", handlerMap.size());
    }
    
    /**
     * 获取消息处理器
     * 
     * @param messageType 消息类型
     * @return 消息处理器，如果不存在则返回null
     */
    public MessageHandler getHandler(int messageType) {
        return handlerMap.get(messageType);
    }
    
    /**
     * 检查是否支持指定的消息类型
     * 
     * @param messageType 消息类型
     * @return 是否支持
     */
    public boolean isSupported(int messageType) {
        return handlerMap.containsKey(messageType);
    }
    
    /**
     * 获取所有支持的消息类型
     * 
     * @return 支持的消息类型集合
     */
    public java.util.Set<Integer> getSupportedMessageTypes() {
        return handlerMap.keySet();
    }
    
    /**
     * 获取处理器数量
     * 
     * @return 处理器数量
     */
    public int getHandlerCount() {
        return handlerMap.size();
    }
}
