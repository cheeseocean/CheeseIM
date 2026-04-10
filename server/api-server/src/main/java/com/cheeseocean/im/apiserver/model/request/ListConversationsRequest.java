package com.cheeseocean.im.apiserver.model.request;

import lombok.Data;

@Data
public class ListConversationsRequest {
    private int limit = 20;
}
