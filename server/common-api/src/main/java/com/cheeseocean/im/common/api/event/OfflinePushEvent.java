package com.cheeseocean.im.common.api.event;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class OfflinePushEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String userId;
    private String conversationId;
    private Long seq;
    private String serverMsgId;
    private String title;
    private String content;
    private Map<String, String> ext = new HashMap<>();

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
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

    public String getServerMsgId() {
        return serverMsgId;
    }

    public void setServerMsgId(String serverMsgId) {
        this.serverMsgId = serverMsgId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Map<String, String> getExt() {
        return ext;
    }

    public void setExt(Map<String, String> ext) {
        this.ext = ext == null ? new HashMap<>() : new HashMap<>(ext);
    }
}
