package com.cheeseocean.im.common.entity.conversation;

import java.io.Serializable;

public class GetAllConversationsReq implements Serializable {

    private static final long serialVersionUID = 1L;

    private String userID;
    private String operationID;

    public GetAllConversationsReq() {
    }

    public GetAllConversationsReq(String userID, String operationID) {
        this.userID = userID;
        this.operationID = operationID;
    }

    public String getUserID() {
        return userID;
    }

    public void setUserID(String userID) {
        this.userID = userID;
    }

    public String getOperationID() {
        return operationID;
    }

    public void setOperationID(String operationID) {
        this.operationID = operationID;
    }
}
