package com.cheeseocean.im.common.api.dto.message;

import com.cheeseocean.im.common.core.enums.MessagePreviewType;

import java.io.Serializable;

public class ConversationLastMessageSummary implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long seq;
    private String senderId;
    private String content;
    private Integer contentType;
    private String previewText;
    private MessagePreviewType previewType;
    private Long sendTime;
    private boolean notification;

    public Long getSeq() {
        return seq;
    }

    public void setSeq(Long seq) {
        this.seq = seq;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getContentType() {
        return contentType;
    }

    public void setContentType(Integer contentType) {
        this.contentType = contentType;
    }

    public String getPreviewText() {
        return previewText;
    }

    public void setPreviewText(String previewText) {
        this.previewText = previewText;
    }

    public MessagePreviewType getPreviewType() {
        return previewType;
    }

    public void setPreviewType(MessagePreviewType previewType) {
        this.previewType = previewType;
    }

    public Long getSendTime() {
        return sendTime;
    }

    public void setSendTime(Long sendTime) {
        this.sendTime = sendTime;
    }

    public boolean isNotification() {
        return notification;
    }

    public void setNotification(boolean notification) {
        this.notification = notification;
    }
}
