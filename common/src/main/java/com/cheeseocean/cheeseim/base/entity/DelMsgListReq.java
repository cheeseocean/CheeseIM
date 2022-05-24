package com.cheeseocean.cheeseim.base.entity;

import java.util.List;

/**
 * @author xxxcrel
 * Created on 2022/5/22
 */
public class DelMsgListReq {
   private String opUserID;
   private String userID;
   private List<Integer> seqList;
   private String operationID;

    public String getOpUserID() {
        return opUserID;
    }

    public void setOpUserID(String opUserID) {
        this.opUserID = opUserID;
    }

    public String getUserID() {
        return userID;
    }

    public void setUserID(String userID) {
        this.userID = userID;
    }

    public List<Integer> getSeqList() {
        return seqList;
    }

    public void setSeqList(List<Integer> seqList) {
        this.seqList = seqList;
    }

    public String getOperationID() {
        return operationID;
    }

    public void setOperationID(String operationID) {
        this.operationID = operationID;
    }
}
