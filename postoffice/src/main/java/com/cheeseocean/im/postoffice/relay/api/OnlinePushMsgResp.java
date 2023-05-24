package com.cheeseocean.im.postoffice.relay.api;

import java.util.List;

public class OnlinePushMsgResp {
    private List<SingleMsgToUser> resp;

    private OnlinePushMsgResp(Builder builder) {setResp(builder.resp);}

    public static Builder newBuilder() {
        return new Builder();
    }


    public List<SingleMsgToUser> getResp() {
        return resp;
    }

    public void setResp(List<SingleMsgToUser> resp) {
        this.resp = resp;
    }

    public static final class Builder {
        private List<SingleMsgToUser> resp;

        private Builder() {}

        public Builder resp(List<SingleMsgToUser> val) {
            resp = val;
            return this;
        }

        public OnlinePushMsgResp build() {
            return new OnlinePushMsgResp(this);
        }
    }
}
