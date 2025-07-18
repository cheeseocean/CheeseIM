package com.cheeseocean.im.common.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;

/**
 * 发送消息请求
 * 
 * @author CheeseIM
 */
public class SendMsgReq implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 消息数据
     */
    @JsonProperty("msgData")
    private Message msgData;
    
    /**
     * 操作ID，用于请求追踪
     */
    @JsonProperty("operationID")
    private String operationID;
    
    public SendMsgReq() {
    }
    
    public SendMsgReq(Message msgData, String operationID) {
        this.msgData = msgData;
        this.operationID = operationID;
    }
    
    public Message getMsgData() {
        return msgData;
    }
    
    public void setMsgData(Message msgData) {
        this.msgData = msgData;
    }
    
    public String getOperationID() {
        return operationID;
    }
    
    public void setOperationID(String operationID) {
        this.operationID = operationID;
    }
    
    @Override
    public String toString() {
        return "SendMsgReq{" +
                "msgData=" + msgData +
                ", operationID='" + operationID + '\'' +
                '}';
    }
}
