package com.cheeseocean.im.apiserver.model.request;

/** HTTP 黑名单操作请求。 */
public class BlacklistActionRequest {

    private String targetUserId;

    public String getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(String targetUserId) {
        this.targetUserId = targetUserId;
    }
}
