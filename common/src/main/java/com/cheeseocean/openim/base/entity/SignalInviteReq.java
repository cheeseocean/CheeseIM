package com.cheeseocean.openim.base.entity;

/**
 * @author xxxcrel
 * Created on 2022/5/22
 */
public class SignalInviteReq {
    private String opUserID;
    private InvitationInfo invitation;
    private OfflinePushInfo offlinePUshInfo;
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

    public OfflinePushInfo getOfflinePUshInfo() {
        return offlinePUshInfo;
    }

    public void setOfflinePUshInfo(OfflinePushInfo offlinePUshInfo) {
        this.offlinePUshInfo = offlinePUshInfo;
    }

    public ParticipantMetaData getParticipant() {
        return participant;
    }

    public void setParticipant(ParticipantMetaData participant) {
        this.participant = participant;
    }
}
