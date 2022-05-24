package com.cheeseocean.cheeseim.push.api;

public class PushMsgResp {
    private int resultCode;

    private PushMsgResp(Builder builder) {setResultCode(builder.resultCode);}

    public static Builder newBuilder() {
        return new Builder();
    }

    public int getResultCode() {
        return resultCode;
    }

    public void setResultCode(int resultCode) {
        this.resultCode = resultCode;
    }


    public static final class Builder {
        private int resultCode;

        private Builder() {}

        public Builder resultCode(int val) {
            resultCode = val;
            return this;
        }

        public PushMsgResp build() {
            return new PushMsgResp(this);
        }
    }
}
