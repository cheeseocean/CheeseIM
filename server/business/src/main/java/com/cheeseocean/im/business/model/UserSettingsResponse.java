package com.cheeseocean.im.business.model;

public class UserSettingsResponse {

    private int globalRecvMsgOpt;

    public UserSettingsResponse(int globalRecvMsgOpt) {
        this.globalRecvMsgOpt = globalRecvMsgOpt;
    }

    public int getGlobalRecvMsgOpt() {
        return globalRecvMsgOpt;
    }

    public void setGlobalRecvMsgOpt(int globalRecvMsgOpt) {
        this.globalRecvMsgOpt = globalRecvMsgOpt;
    }
}
