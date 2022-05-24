package com.cheeseocean.cheeseim.base.entity;

/**
 * @author xxxcrel
 * Created on 2022/5/22
 */
public class UserSendMsgResp {
    private String serverMsgID;
    private String clientMsgID;
    private int sendTime;

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

    public int getSendTime() {
        return sendTime;
    }

    public void setSendTime(int sendTime) {
        this.sendTime = sendTime;
    }
}
