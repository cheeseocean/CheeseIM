package com.cheeseocean.im.common.entity;

public class Resp {
    int reqIdentifier;

    String operationID;

    int msgIncr;

    int errCode;

    String errMsg;

    byte[] data;

    public int getReqIdentifier() {
        return reqIdentifier;
    }

    public void setReqIdentifier(int reqIdentifier) {
        this.reqIdentifier = reqIdentifier;
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

    public int getErrCode() {
        return errCode;
    }

    public void setErrCode(int errCode) {
        this.errCode = errCode;
    }

    public String getErrMsg() {
        return errMsg;
    }

    public void setErrMsg(String errMsg) {
        this.errMsg = errMsg;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }
}
