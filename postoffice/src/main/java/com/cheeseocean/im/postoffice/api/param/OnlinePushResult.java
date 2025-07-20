package com.cheeseocean.im.postoffice.api.param;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 在线推送结果类
 */
public class OnlinePushResult extends ArrayList<OnlinePushResult> implements Serializable {
    private boolean                  success;
    private String                   userID;
    private List<PushPlatformResult> platformResults;

    public OnlinePushResult() {
    }

    public OnlinePushResult(boolean success, String userID, List<PushPlatformResult> platformResults) {
        this.success = success;
        this.userID = userID;
        this.platformResults = platformResults;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getUserId() {
        return userID;
    }

    public void setUserId(String userID) {
        this.userID = userID;
    }

    public List<PushPlatformResult> getPlatformResults() {
        return platformResults;
    }

    public void setPlatformResults(List<PushPlatformResult> platformResults) {
    }

    /**
     * 创建失败的推送结果
     */
    public static OnlinePushResult failure() {
        OnlinePushResult result = new OnlinePushResult();
        result.setSuccess(false);
        return result;
    }

    /**
     * 创建成功的推送结果
     */
    public static OnlinePushResult success() {
        OnlinePushResult result = new OnlinePushResult();
        result.setSuccess(true);
        return result;
    }

    /**
     * 推送终端结果类
     */
    public class PushPlatformResult implements Serializable {
        private Integer resultCode;
        private String  receiverId;
        private String  platformId;

        public Integer getResultCode() {
            return resultCode;
        }

        public void setResultCode(Integer resultCode) {
            this.resultCode = resultCode;
        }

        public String getReceiverId() {
            return receiverId;
        }

        public void setReceiverId(String receiverId) {
            this.receiverId = receiverId;
        }

        public String getPlatformId() {
            return platformId;
        }

        public void setPlatformId(String platformId) {
            this.platformId = platformId;
        }
    }
}
