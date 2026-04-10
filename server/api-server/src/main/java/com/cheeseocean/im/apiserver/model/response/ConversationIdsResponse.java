package com.cheeseocean.im.apiserver.model.response;

import lombok.Data;

import java.util.List;

@Data
public class ConversationIdsResponse {
    private List<String> conversationIds;
}
