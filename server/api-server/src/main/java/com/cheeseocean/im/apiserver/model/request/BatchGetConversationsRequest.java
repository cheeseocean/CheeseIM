package com.cheeseocean.im.apiserver.model.request;

import lombok.Data;

import java.util.List;

@Data
public class BatchGetConversationsRequest {
    private List<String> conversationIds;
}
