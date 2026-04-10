package com.cheeseocean.im.apiserver.model.request;

import lombok.Data;

@Data
public class ListConversationMessagesRequest {
    private String conversationId;
    private int limit = 50;
}
