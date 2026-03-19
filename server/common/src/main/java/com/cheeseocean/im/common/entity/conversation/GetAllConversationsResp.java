package com.cheeseocean.im.common.entity.conversation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class GetAllConversationsResp implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer errCode = 0;
    private String errMsg = "";
    private List<Object> conversations = new ArrayList<>();

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

    public List<Object> getConversations() {
        return conversations;
    }

    public void setConversations(List<?> conversations) {
        this.conversations = new ArrayList<>(conversations);
    }
}
