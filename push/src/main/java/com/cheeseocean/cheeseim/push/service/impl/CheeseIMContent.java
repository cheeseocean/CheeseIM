package com.cheeseocean.cheeseim.push.service.impl;

public class CheeseIMContent {
    private int sessionType;
    private String from;
    private String to;
    int seq;

    private CheeseIMContent(Builder builder) {
        setSessionType(builder.sessionType);
        setFrom(builder.from);
        setTo(builder.to);
        setSeq(builder.seq);
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public int getSessionType() {
        return sessionType;
    }

    public void setSessionType(int sessionType) {
        this.sessionType = sessionType;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public int getSeq() {
        return seq;
    }

    public void setSeq(int seq) {
        this.seq = seq;
    }

    public static final class Builder {
        private int sessionType;
        private String from;
        private String to;
        private int seq;

        private Builder() {}

        public Builder sessionType(int val) {
            sessionType = val;
            return this;
        }

        public Builder from(String val) {
            from = val;
            return this;
        }

        public Builder to(String val) {
            to = val;
            return this;
        }

        public Builder seq(int val) {
            seq = val;
            return this;
        }

        public CheeseIMContent build() {
            return new CheeseIMContent(this);
        }
    }
}
