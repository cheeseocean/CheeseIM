package com.cheeseocean.im.postoffice.handler;

import com.cheeseocean.im.common.api.protocol.ClientEnvelope;
import com.cheeseocean.im.common.core.enums.CommandType;
import com.cheeseocean.im.postoffice.connection.UserConnection;
import com.cheeseocean.im.postoffice.protocol.WSMessage;

/**
 * 消息处理器接口
 * 定义各种消息类型的处理方法
 * 
 * @author CheeseIM
 */
public interface MessageHandler {
    
    /**
     * 处理消息
     * 
     * @param connection 用户连接
     * @param envelope 客户端统一消息包
     * @return 处理结果
     */
    HandleResult handle(UserConnection connection, ClientEnvelope envelope);
    
    /**
     * 获取支持的命令类型
     * 
     * @return 命令类型
     */
    CommandType getSupportedCommand();
    
    /**
     * 处理结果类
     */
    class HandleResult {
        private boolean success;
        private String errorMessage;
        private WSMessage responseMessage;
        private boolean shouldClose;
        
        public HandleResult() {}
        
        public HandleResult(boolean success) {
            this.success = success;
        }
        
        public HandleResult(boolean success, String errorMessage) {
            this.success = success;
            this.errorMessage = errorMessage;
        }
        
        public static HandleResult success() {
            return new HandleResult(true);
        }
        
        public static HandleResult success(WSMessage responseMessage) {
            HandleResult result = new HandleResult(true);
            result.setResponseMessage(responseMessage);
            return result;
        }
        
        public static HandleResult failure(String errorMessage) {
            return new HandleResult(false, errorMessage);
        }
        
        public static HandleResult failure(String errorMessage, WSMessage responseMessage) {
            HandleResult result = new HandleResult(false, errorMessage);
            result.setResponseMessage(responseMessage);
            return result;
        }
        
        public static HandleResult failureAndClose(String errorMessage, WSMessage responseMessage) {
            HandleResult result = failure(errorMessage, responseMessage);
            result.setShouldClose(true);
            return result;
        }
        
        // Getter and Setter
        public boolean isSuccess() {
            return success;
        }
        
        public void setSuccess(boolean success) {
            this.success = success;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }
        
        public WSMessage getResponseMessage() {
            return responseMessage;
        }
        
        public void setResponseMessage(WSMessage responseMessage) {
            this.responseMessage = responseMessage;
        }
        
        public boolean isShouldClose() {
            return shouldClose;
        }

        public boolean isCloseConnection() {
            return shouldClose;
        }
        
        public void setShouldClose(boolean shouldClose) {
            this.shouldClose = shouldClose;
        }
        
        @Override
        public String toString() {
            return "HandleResult{" +
                    "success=" + success +
                    ", errorMessage='" + errorMessage + '\'' +
                    ", responseMessage=" + responseMessage +
                    ", shouldClose=" + shouldClose +
                    '}';
        }
    }
}
