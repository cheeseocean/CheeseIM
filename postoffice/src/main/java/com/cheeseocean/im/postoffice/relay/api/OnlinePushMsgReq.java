package com.cheeseocean.im.postoffice.relay.api;

import com.cheeseocean.im.common.entity.CheeseMessage;

/**
 * @author xxxcrel
 * Created on 2022/5/20
 */
public class OnlinePushMsgReq {
    private String operationID;
    private CheeseMessage CheeseMessage;
    private String pushToUserID;

    private OnlinePushMsgReq(Builder builder) {
        setOperationID(builder.operationID);
        setMsgData(builder.CheeseMessage);
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

        public OnlinePushMsgReq build() {
            return new OnlinePushMsgReq(this);
        }
    }
}
