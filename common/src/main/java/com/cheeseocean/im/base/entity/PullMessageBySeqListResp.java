package com.cheeseocean.im.base.entity;

import java.util.List;

/**
 * @author xxxcrel
 * Created on 2022/5/22
 */
public class PullMessageBySeqListResp {
    private int errCode;
    private String errMsg;
    private List<MsgData> list;

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

    public List<MsgData> getList() {
        return list;
    }

    public void setList(List<MsgData> list) {
        this.list = list;
    }
}
