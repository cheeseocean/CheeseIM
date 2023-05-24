package com.cheeseocean.im.postoffice.relay.api;

import java.util.List;

public class GetUserOnlineStatusReq {
    private List<String> userIDList;
    private String operationID;
    private String opUserID;

    private GetUserOnlineStatusReq(Builder builder) {
        setUserIDList(builder.userIDList);
        setOperationID(builder.operationID);
        setOpUserID(builder.opUserID);
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public List<String> getUserIDList() {
        return userIDList;
    }

    public void setUserIDList(List<String> userIDList) {
        this.userIDList = userIDList;
    }

    public String getOperationID() {
        return operationID;
    }

    public void setOperationID(String operationID) {
        this.operationID = operationID;
    }

    public String getOpUserID() {
        return opUserID;
    }

    public void setOpUserID(String opUserID) {
        this.opUserID = opUserID;
    }

    public static final class Builder {
        private List<String> userIDList;
        private String operationID;
        private String opUserID;

        private Builder() {}

        public Builder userIDList(List<String> val) {
            userIDList = val;
            return this;
        }

        public Builder operationID(String val) {
            operationID = val;
            return this;
        }

        public Builder opUserID(String val) {
            opUserID = val;
            return this;
        }

        public GetUserOnlineStatusReq build() {
            return new GetUserOnlineStatusReq(this);
        }
    }
}
