package com.cheeseocean.im.common.api.event;

import com.cheeseocean.im.common.api.dto.message.SequencedMessage;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class HistoryEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String conversationId;
    private Long beginSeq;
    private Long endSeq;
    private List<SequencedMessage> messages = new ArrayList<>();

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public Long getBeginSeq() {
        return beginSeq;
    }

    public void setBeginSeq(Long beginSeq) {
        this.beginSeq = beginSeq;
    }

    public Long getEndSeq() {
        return endSeq;
    }

    public void setEndSeq(Long endSeq) {
        this.endSeq = endSeq;
    }

    public List<SequencedMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<SequencedMessage> messages) {
        this.messages = messages == null ? new ArrayList<>() : new ArrayList<>(messages);
    }
}
