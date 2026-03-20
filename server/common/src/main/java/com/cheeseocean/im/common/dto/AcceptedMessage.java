package com.cheeseocean.im.common.dto;

import java.io.Serializable;

public class AcceptedMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private String serverMsgId;
    private String clientMsgId;
    private String conversationId;
    private Long conversationSeq;

    public static AcceptedMessage of(String serverMsgId, String clientMsgId, String conversationId, long conversationSeq) {
        AcceptedMessage accepted = new AcceptedMessage();
        accepted.setServerMsgId(serverMsgId);
        accepted.setClientMsgId(clientMsgId);
        accepted.setConversationId(conversationId);
        accepted.setConversationSeq(conversationSeq);
        return accepted;
    }

    public String getServerMsgId() {
        return serverMsgId;
    }

    public void setServerMsgId(String serverMsgId) {
        this.serverMsgId = serverMsgId;
    }

    public String getClientMsgId() {
        return clientMsgId;
    }

    public void setClientMsgId(String clientMsgId) {
        this.clientMsgId = clientMsgId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public Long getConversationSeq() {
        return conversationSeq;
    }

    public void setConversationSeq(Long conversationSeq) {
        this.conversationSeq = conversationSeq;
    }
}
