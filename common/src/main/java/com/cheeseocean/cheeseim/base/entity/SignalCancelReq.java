package com.cheeseocean.cheeseim.base.entity;

/**
 * @author xxxcrel
 * Created on 2022/5/22
 */
public class SignalCancelReq {
    private String opUserID;
    private InvitationInfo invitation;
    private OfflinePushInfo offlinePushInfo;
    private ParticipantMetaData participant;

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

    public ParticipantMetaData getParticipant() {
        return participant;
    }

    public void setParticipant(ParticipantMetaData participant) {
        this.participant = participant;
    }
}
