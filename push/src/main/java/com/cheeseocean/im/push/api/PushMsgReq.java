package com.cheeseocean.im.push.api;

import com.cheeseocean.im.common.entity.MsgData;

public class PushMsgReq {
    private String operationID;
    private MsgData msgData;
    private String pushToUserID;

    private PushMsgReq(Builder builder) {
        operationID = builder.operationID;
        msgData = builder.msgData;
        pushToUserID = builder.pushToUserID;
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

    public static Builder newBuilder() {
        return new Builder();
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

        public PushMsgReq build() {
            return new PushMsgReq(this);
        }
    }
}
