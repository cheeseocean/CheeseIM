package com.cheeseocean.im.common.api.dto.message;

import java.io.Serializable;

public class ReadReceiptPayload implements Serializable {

    private static final long serialVersionUID = 1L;

    private String conversationId;
    private String serverMsgId;
    private Long seq;
    private Long receiptTime;

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getServerMsgId() {
        return serverMsgId;
    }

    public void setServerMsgId(String serverMsgId) {
        this.serverMsgId = serverMsgId;
    }

    public Long getSeq() {
        return seq;
    }

    public void setSeq(Long seq) {
        this.seq = seq;
    }

    public Long getReceiptTime() {
        return receiptTime;
    }

    public void setReceiptTime(Long receiptTime) {
        this.receiptTime = receiptTime;
    }
}
