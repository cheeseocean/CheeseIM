package com.cheeseocean.im.common.entity;

import java.util.List;

/**
 * @author xxxcrel
 * Created on 2022/5/22
 */
public class PullMessageBySeqListReq {
    private String userID;
    private String operationID;
    private List<Integer> seqList;

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

    public List<Integer> getSeqList() {
        return seqList;
    }

    public void setSeqList(List<Integer> seqList) {
        this.seqList = seqList;
    }
}
