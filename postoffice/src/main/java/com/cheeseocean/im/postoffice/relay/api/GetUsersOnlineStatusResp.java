package com.cheeseocean.im.postoffice.relay.api;

import java.util.List;

public class GetUsersOnlineStatusResp {
    private int errCode;
    private String errMsg;
    private List<SuccessResult> successResult;
    private List<FailedDetail> failedResult;

    private GetUsersOnlineStatusResp(Builder builder) {
        setErrCode(builder.errCode);
        setErrMsg(builder.errMsg);
        setSuccessResult(builder.successResult);
        setFailedResult(builder.failedResult);
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class SuccessDetail {
        private String platform;
        private String status;
    }
    public static class FailedDetail {
        private String userID;
        private int errCode;
        private String errMsg;
    }
    public static class SuccessResult {
        private String userID;
        private String status;
        private List<SuccessDetail> detailPlatformStatus;
    }


    public int getErrCode() {
        return errCode;
    }

    public void setErrCode(int errCode) {
        this.errCode = errCode;
    }

    public String getErrMsg() {
        return errMsg;
    }

    public void setErrMsg(String errMsg) {
        this.errMsg = errMsg;
    }

    public List<SuccessResult> getSuccessResult() {
        return successResult;
    }

    public void setSuccessResult(List<SuccessResult> successResult) {
        this.successResult = successResult;
    }

    public List<FailedDetail> getFailedResult() {
        return failedResult;
    }

    public void setFailedResult(List<FailedDetail> failedResult) {
        this.failedResult = failedResult;
    }

    public static final class Builder {
        private int errCode;
        private String errMsg;
        private List<SuccessResult> successResult;
        private List<FailedDetail> failedResult;

        private Builder() {}

        public Builder errCode(int val) {
            errCode = val;
            return this;
        }

        public Builder errMsg(String val) {
            errMsg = val;
            return this;
        }

        public Builder successResult(List<SuccessResult> val) {
            successResult = val;
            return this;
        }

        public Builder failedResult(List<FailedDetail> val) {
            failedResult = val;
            return this;
        }

        public GetUsersOnlineStatusResp build() {
            return new GetUsersOnlineStatusResp(this);
        }
    }
}
