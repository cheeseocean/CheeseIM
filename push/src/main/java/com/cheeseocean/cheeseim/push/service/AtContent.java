package com.cheeseocean.cheeseim.push.service;

import java.util.List;

public class AtContent {
    private String text;
    private List<String> atUserList;
    private boolean isAtSelf;

    private AtContent(Builder builder) {
        setText(builder.text);
        setAtUserList(builder.atUserList);
        setAtSelf(builder.isAtSelf);
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public List<String> getAtUserList() {
        return atUserList;
    }

    public void setAtUserList(List<String> atUserList) {
        this.atUserList = atUserList;
    }

    public boolean isAtSelf() {
        return isAtSelf;
    }

    public void setAtSelf(boolean atSelf) {
        isAtSelf = atSelf;
    }

    public static final class Builder {
        private String text;
        private List<String> atUserList;
        private boolean isAtSelf;

        private Builder() {}

        public Builder text(String val) {
            text = val;
            return this;
        }

        public Builder atUserList(List<String> val) {
            atUserList = val;
            return this;
        }

        public Builder isAtSelf(boolean val) {
            isAtSelf = val;
            return this;
        }

        public AtContent build() {
            return new AtContent(this);
        }
    }
}
