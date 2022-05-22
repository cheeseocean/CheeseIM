package com.cheeseocean.openim.base.entity;

/**
 * @author xxxcrel
 * Created on 2022/5/22
 */
public class SignalHungUpReq {
    private String opUserID;
    private InvitationInfo invitation;
    private OfflinePushInfo offlinePUshInfo;

    public String getOpUserID() {
        return opUserID;
    }

    public void setOpUserID(String opUserID) {
        this.opUserID = opUserID;
    }

    public InvitationInfo getInvitation() {
        return invitation;
    }

    public void setInvitation(InvitationInfo invitation) {
        this.invitation = invitation;
    }

    public OfflinePushInfo getOfflinePUshInfo() {
        return offlinePUshInfo;
    }

    public void setOfflinePUshInfo(OfflinePushInfo offlinePUshInfo) {
        this.offlinePUshInfo = offlinePUshInfo;
    }
}
