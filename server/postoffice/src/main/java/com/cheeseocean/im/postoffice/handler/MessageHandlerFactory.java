package com.cheeseocean.im.postoffice.handler;

import com.cheeseocean.im.common.core.enums.CommandType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    
    private final Map<CommandType, MessageHandler> handlerMap = new EnumMap<>(CommandType.class);
    
    /**
     * 初始化处理器映射
     */
    @PostConstruct
    public void init() {
        for (MessageHandler handler : messageHandlers) {
            CommandType commandType = handler.getSupportedCommand();
            if (commandType == null) {
                logger.debug("Skipping message handler without command binding: {}", handler.getClass().getSimpleName());
                continue;
            }
            handlerMap.put(commandType, handler);

            logger.info("Registered command handler: {} -> {}",
                    commandType,
                    handler.getClass().getSimpleName());
        }
        
        logger.info("MessageHandlerFactory initialized with {} handlers", handlerMap.size());
    }
    
    /**
     * 获取消息处理器
     * 
     * @param commandType 命令类型
     * @return 消息处理器，如果不存在则返回null
     */
    public MessageHandler getHandler(CommandType commandType) {
        return handlerMap.get(commandType);
    }
    
    /**
     * 检查是否支持指定的命令类型
     * 
     * @param commandType 命令类型
     * @return 是否支持
     */
    public boolean isSupported(CommandType commandType) {
        return handlerMap.containsKey(commandType);
    }
    
    /**
     * 获取所有支持的命令类型
     * 
     * @return 支持的命令类型集合
     */
    public Set<CommandType> getSupportedCommands() {
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
