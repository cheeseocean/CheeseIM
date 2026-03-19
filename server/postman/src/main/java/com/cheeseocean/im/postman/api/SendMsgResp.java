package com.cheeseocean.im.postman.api;

import java.io.Serializable;

public class SendMsgResp implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer errCode;
    private String errMsg;
    private String serverMsgID;
    private String clientMsgID;
    private Long sendTime;

    public static SendMsgResp success(String serverMsgID, String clientMsgID, Long sendTime) {
        SendMsgResp resp = new SendMsgResp();
        resp.setErrCode(0);
        resp.setErrMsg("");
        resp.setServerMsgID(serverMsgID);
        resp.setClientMsgID(clientMsgID);
        resp.setSendTime(sendTime);
        return resp;
    }

    public static SendMsgResp error(Integer errCode, String errMsg) {
        SendMsgResp resp = new SendMsgResp();
        resp.setErrCode(errCode);
        resp.setErrMsg(errMsg);
        return resp;
    }

    public Integer getErrCode() {
        return errCode;
    }

    public void setErrCode(Integer errCode) {
        this.errCode = errCode;
    }

    public String getErrMsg() {
        return errMsg;
    }

    public void setErrMsg(String errMsg) {
        this.errMsg = errMsg;
    }

    public String getServerMsgID() {
        return serverMsgID;
    }

    public void setServerMsgID(String serverMsgID) {
        this.serverMsgID = serverMsgID;
    }

    public String getClientMsgID() {
        return clientMsgID;
    }

    public void setClientMsgID(String clientMsgID) {
        this.clientMsgID = clientMsgID;
    }

    public Long getSendTime() {
        return sendTime;
    }

    public void setSendTime(Long sendTime) {
        this.sendTime = sendTime;
    }
}
