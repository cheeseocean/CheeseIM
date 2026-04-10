package com.cheeseocean.im.apiserver.model.request;

import lombok.Data;

@Data
public class SendFriendRequestRequest {
    private String friendUserId;
    private String requestMessage;
}
