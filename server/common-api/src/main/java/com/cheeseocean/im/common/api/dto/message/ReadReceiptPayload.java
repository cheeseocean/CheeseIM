package com.cheeseocean.im.common.api.dto.message;

import com.cheeseocean.im.common.core.enums.ReceiptType;

import java.io.Serializable;

public class ReadReceiptPayload implements Serializable {

    private static final long serialVersionUID = 1L;

    private ReceiptType receiptType;
    private String conversationId;
    private Long seq;

    public ReceiptType getReceiptType() {
        return receiptType;
    }

    public void setReceiptType(ReceiptType receiptType) {
        this.receiptType = receiptType;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public Long getSeq() {
        return seq;
    }

    public void setSeq(Long seq) {
        this.seq = seq;
    }
}
