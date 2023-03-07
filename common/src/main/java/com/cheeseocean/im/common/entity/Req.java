package com.cheeseocean.im.common.entity;

public class Req {
    int reqIdentifier;

    String token;

    String sendID;

    String operationID;

    int msgIncr;

    byte[] data;

    public int getReqIdentifier() {
        return reqIdentifier;
    }

    public void setReqIdentifier(int reqIdentifier) {
        this.reqIdentifier = reqIdentifier;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getSendID() {
        return sendID;
    }

    public void setSendID(String sendID) {
        this.sendID = sendID;
    }

    public String getOperationID() {
        return operationID;
    }

    public void setOperationID(String operationID) {
        this.operationID = operationID;
    }

    public int getMsgIncr() {
        return msgIncr;
    }

    public void setMsgIncr(int msgIncr) {
        this.msgIncr = msgIncr;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }
}
