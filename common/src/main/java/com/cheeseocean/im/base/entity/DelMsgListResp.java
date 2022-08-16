package com.cheeseocean.im.base.entity;

/**
 * @author xxxcrel
 * Created on 2022/5/22
 */
public class DelMsgListResp {
    private int errCode;
    private String errMsg;

    public int getErrCode() {
        return errCode;
    }

    public void setErrCode(int errCode) {
        this.errCode = errCode;
    }

    public String getErrMsg() {
        return errMsg;
    }

    public void setErrMsg(String errMsg) {
        this.errMsg = errMsg;
    }
}
