package com.cheeseocean.im.postoffice.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 协议转换工具类
 * 负责在WebSocket协议和TCP协议之间进行消息转换
 * 
 * @author CheeseIM
 */
@Component
public class ProtocolConverter {
    
    private static final Logger logger = LoggerFactory.getLogger(ProtocolConverter.class);
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * 将WebSocket消息转换为TCP消息
     */
    public CheeseMessage wsToTcp(WSMessage wsMessage) {
        try {
            if (wsMessage == null) {
                return null;
            }
            
            CheeseMessage cheeseMessage = new CheeseMessage();
            
            // 转换消息类型
            cheeseMessage.setMsgType(CheeseMessageType.wsToTcpMessageType(wsMessage.getMsgType()));
            
            // 设置操作ID
            cheeseMessage.setOperationID(wsMessage.getOperationID());
            
            // 设置时间戳
            cheeseMessage.setTimestamp(wsMessage.getSendTime() != null ?
                                   wsMessage.getSendTime() : System.currentTimeMillis());
            
            // 转换数据
            String data = convertDataToJson(wsMessage);
            cheeseMessage.setData(data);
            
            return cheeseMessage;
            
        } catch (Exception e) {
            logger.error("Failed to convert WebSocket message to TCP message", e);
            return null;
        }
    }
    
    /**
     * 将TCP消息转换为WebSocket消息
     */
    public WSMessage tcpToWs(CheeseMessage cheeseMessage) {
        try {
            if (cheeseMessage == null) {
                return null;
            }
            
            WSMessage wsMessage = new WSMessage();
            
            // 转换消息类型
            wsMessage.setMsgType(CheeseMessageType.tcpToWsMessageType(cheeseMessage.getMsgType()));
            
            // 设置操作ID
            wsMessage.setOperationID(cheeseMessage.getOperationID());
            
            // 设置时间戳
            wsMessage.setSendTime(cheeseMessage.getTimestamp());
            
            // 转换数据
            Object data = convertJsonToData(cheeseMessage.getData());
            wsMessage.setData(data);
            
            return wsMessage;
            
        } catch (Exception e) {
            logger.error("Failed to convert TCP message to WebSocket message", e);
            return null;
        }
    }
    
    /**
     * 将WebSocket消息数据转换为JSON字符串
     */
    private String convertDataToJson(WSMessage wsMessage) {
        try {
            if (wsMessage.getData() == null) {
                return null;
            }
            
            // 构建完整的消息数据
            MessageData messageData = new MessageData();
            messageData.setData(wsMessage.getData());
            messageData.setSendID(wsMessage.getSendID());
            messageData.setRecvID(wsMessage.getRecvID());
            messageData.setMsgID(wsMessage.getMsgID());
            messageData.setEx(wsMessage.getEx());
            
            return objectMapper.writeValueAsString(messageData);
            
        } catch (Exception e) {
            logger.warn("Failed to convert data to JSON, using toString: {}", e.getMessage());
            return wsMessage.getData().toString();
        }
    }
    
    /**
     * 将JSON字符串转换为WebSocket消息数据
     */
    private Object convertJsonToData(String jsonData) {
        try {
            if (jsonData == null || jsonData.trim().isEmpty()) {
                return null;
            }
            
            // 尝试解析为MessageData对象
            MessageData messageData = objectMapper.readValue(jsonData, MessageData.class);
            return messageData.getData();
            
        } catch (Exception e) {
            logger.debug("Failed to parse JSON data, returning as string: {}", e.getMessage());
            return jsonData;
        }
    }
    
    /**
     * 创建错误响应的TCP消息
     */
    public CheeseMessage createTcpErrorResponse(String operationID, int errorCode, String errorMessage) {
        return CheeseMessage.errorResp(operationID, errorCode, errorMessage);
    }
    
    /**
     * 创建错误响应的WebSocket消息
     */
    public WSMessage createWsErrorResponse(String operationID, int errorCode, String errorMessage) {
        return WSMessage.errorResp(operationID, errorCode, errorMessage);
    }
    
    /**
     * 检查消息类型是否需要转换
     */
    public boolean needsConversion(int messageType, String protocol) {
        // 某些消息类型可能不需要转换，直接透传
        return true;
    }
    
    /**
     * 消息数据包装类
     */
    public static class MessageData {
        private Object data;
        private String sendID;
        private String recvID;
        private String msgID;
        private Object ex;
        
        public MessageData() {}
        
        // Getter and Setter methods
        public Object getData() {
            return data;
        }
        
        public void setData(Object data) {
            this.data = data;
        }
        
        public String getSendID() {
            return sendID;
        }
        
        public void setSendID(String sendID) {
            this.sendID = sendID;
        }
        
        public String getRecvID() {
            return recvID;
        }
        
        public void setRecvID(String recvID) {
            this.recvID = recvID;
        }
        
        public String getMsgID() {
            return msgID;
        }
        
        public void setMsgID(String msgID) {
            this.msgID = msgID;
        }
        
        public Object getEx() {
            return ex;
        }
        
        public void setEx(Object ex) {
            this.ex = ex;
        }
    }
}
