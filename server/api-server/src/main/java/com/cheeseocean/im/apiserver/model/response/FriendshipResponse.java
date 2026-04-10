package com.cheeseocean.im.apiserver.model.response;

import lombok.Data;

@Data
public class FriendshipResponse {
    private String id;
    private String userId;
    private String friendId;
    private String remark;
    private int addSource;
    private String operatorId;
    private boolean pinned;
    private String ex;
    private long createdAt;
}
