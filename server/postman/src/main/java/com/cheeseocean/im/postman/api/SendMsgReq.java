package com.cheeseocean.im.postman.api;

import com.cheeseocean.im.common.api.dto.message.Message;

import java.io.Serializable;

public class SendMsgReq implements Serializable {

    private static final long serialVersionUID = 1L;

    private Message message;
    private String operationID;

    public SendMsgReq() {
    }

    public SendMsgReq(Message message, String operationID) {
        this.message = message;
        this.operationID = operationID;
    }

    public Message getMessage() {
        return message;
    }

    public Message getMsgData() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }

    public void setMsgData(Message message) {
        this.message = message;
    }

    public String getOperationID() {
        return operationID;
    }

    public void setOperationID(String operationID) {
        this.operationID = operationID;
    }
}
