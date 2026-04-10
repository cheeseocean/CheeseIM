package com.cheeseocean.im.apiserver.model.response;

import lombok.Data;

@Data
public class FriendRequestResponse {
    private String fromUserId;
    private String toUserId;
    private String reqMsg;
    private int handleResult;
    private String handleMsg;
    private String handlerUserId;
    private long handleTime;
    private String ex;
    private long createTime;
    private long updatedAt;
}
