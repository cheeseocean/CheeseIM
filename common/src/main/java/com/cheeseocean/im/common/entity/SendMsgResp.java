package com.cheeseocean.im.common.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;

/**
 * 发送消息响应
 * 
 * @author CheeseIM
 */
public class SendMsgResp implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 服务端消息ID
     */
    @JsonProperty("serverMsgID")
    private String serverMsgID;
    
    /**
     * 客户端消息ID
     */
    @JsonProperty("clientMsgID")
    private String clientMsgID;
    
    /**
     * 发送时间
     */
    @JsonProperty("sendTime")
    private Long sendTime;
    
    /**
     * 错误码
     */
    @JsonProperty("errCode")
    private Integer errCode;
    
    /**
     * 错误信息
     */
    @JsonProperty("errMsg")
    private String errMsg;
    
    public SendMsgResp() {
    }
    
    public SendMsgResp(String serverMsgID, String clientMsgID, Long sendTime) {
        this.serverMsgID = serverMsgID;
        this.clientMsgID = clientMsgID;
        this.sendTime = sendTime;
        this.errCode = 0;
        this.errMsg = "success";
    }
    
    public static SendMsgResp success(String serverMsgID, String clientMsgID, Long sendTime) {
        return new SendMsgResp(serverMsgID, clientMsgID, sendTime);
    }
    
    public static SendMsgResp error(Integer errCode, String errMsg) {
        SendMsgResp resp = new SendMsgResp();
        resp.setErrCode(errCode);
        resp.setErrMsg(errMsg);
        return resp;
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
    
    @Override
    public String toString() {
        return "SendMsgResp{" +
                "serverMsgID='" + serverMsgID + '\'' +
                ", clientMsgID='" + clientMsgID + '\'' +
                ", sendTime=" + sendTime +
                ", errCode=" + errCode +
                ", errMsg='" + errMsg + '\'' +
                '}';
    }
}
