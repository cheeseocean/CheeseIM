package com.cheeseocean.im.common.api.event;

import com.cheeseocean.im.common.api.dto.message.SequencedMessage;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class DeliveryEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String conversationId;
    private SequencedMessage message;
    private List<String> targetUserIds = new ArrayList<>();

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public SequencedMessage getMessage() {
        return message;
    }

    public void setMessage(SequencedMessage message) {
        this.message = message;
    }

    public List<String> getTargetUserIds() {
        return targetUserIds;
    }

    public void setTargetUserIds(List<String> targetUserIds) {
        this.targetUserIds = targetUserIds == null ? new ArrayList<>() : new ArrayList<>(targetUserIds);
    }
}
