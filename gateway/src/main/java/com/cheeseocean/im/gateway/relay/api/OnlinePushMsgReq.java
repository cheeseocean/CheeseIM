package com.cheeseocean.im.gateway.relay.api;

import com.cheeseocean.im.common.entity.MsgData;

/**
 * @author xxxcrel
 * Created on 2022/5/20
 */
public class OnlinePushMsgReq {
    private String operationID;
    private MsgData msgData;
    private String pushToUserID;

    private OnlinePushMsgReq(Builder builder) {
        setOperationID(builder.operationID);
        setMsgData(builder.msgData);
        setPushToUserID(builder.pushToUserID);
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public String getOperationID() {
        return operationID;
    }

    public void setOperationID(String operationID) {
        this.operationID = operationID;
    }

    public MsgData getMsgData() {
        return msgData;
    }

    public void setMsgData(MsgData msgData) {
        this.msgData = msgData;
    }

    public String getPushToUserID() {
        return pushToUserID;
    }

    public void setPushToUserID(String pushToUserID) {
        this.pushToUserID = pushToUserID;
    }

    public static final class Builder {
        private String operationID;
        private MsgData msgData;
        private String pushToUserID;

        private Builder() {}

        public Builder operationID(String val) {
            operationID = val;
            return this;
        }

        public Builder msgData(MsgData val) {
            msgData = val;
            return this;
        }

        public Builder pushToUserID(String val) {
            pushToUserID = val;
            return this;
        }

        public OnlinePushMsgReq build() {
            return new OnlinePushMsgReq(this);
        }
    }
}
