package com.cheeseocean.im.common.entity;

/**
 * @author xxxcrel
 * Created on 2022/5/22
 */
public class SignalAcceptReq {
    private String opUserID;
    private InvitationInfo invitation;
    private OfflinePushInfo offlinePushInfo;
    private ParticipantMetaData priticipant;
    private int opUserPlatformID;

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

    public OfflinePushInfo getOfflinePushInfo() {
        return offlinePushInfo;
    }

    public void setOfflinePushInfo(OfflinePushInfo offlinePushInfo) {
        this.offlinePushInfo = offlinePushInfo;
    }

    public ParticipantMetaData getPriticipant() {
        return priticipant;
    }

    public void setPriticipant(ParticipantMetaData priticipant) {
        this.priticipant = priticipant;
    }

    public int getOpUserPlatformID() {
        return opUserPlatformID;
    }

    public void setOpUserPlatformID(int opUserPlatformID) {
        this.opUserPlatformID = opUserPlatformID;
    }
}
