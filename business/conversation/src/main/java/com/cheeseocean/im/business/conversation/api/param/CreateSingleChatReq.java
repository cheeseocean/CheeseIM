package com.cheeseocean.im.business.conversation.api.param;

import java.io.Serializable;

/**
 * create single chat request
 *
 * @author xxxcrel
 * @date 2025/7/19 20:16
 */
public class CreateSingleChatReq implements Serializable {
    /**
     * sender id
     */
    private String senderId;
    /**
     * receiver id
     */
    private String receiverId;
    /**
     * conversation id
     */
    private String conversationId;

    //getter and setter
    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(String receiverId) {
        this.receiverId = receiverId;
    }
}
