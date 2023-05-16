package com.cheeseocean.im.push.api;

import com.cheeseocean.im.common.entity.CheeseMessage;

public class PushMsgReq {
    private String operationID;
    private CheeseMessage CheeseMessage;
    private String pushToUserID;

    private PushMsgReq(Builder builder) {
        operationID = builder.operationID;
        CheeseMessage = builder.CheeseMessage;
        pushToUserID = builder.pushToUserID;
    }

    public String getOperationID() {
        return operationID;
    }

    public void setOperationID(String operationID) {
        this.operationID = operationID;
    }

    public CheeseMessage getMsgData() {
        return CheeseMessage;
    }

    public void setMsgData(CheeseMessage CheeseMessage) {
        this.CheeseMessage = CheeseMessage;
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
        private CheeseMessage CheeseMessage;
        private String pushToUserID;

        private Builder() {}

        public Builder operationID(String val) {
            operationID = val;
            return this;
        }

        public Builder msgData(CheeseMessage val) {
            CheeseMessage = val;
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
