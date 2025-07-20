package com.cheeseocean.im.business.conversation.api.param;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/**
 * 会话响应基类 - 提供通用的错误处理字段
 *
 * @author CheeseIM
 */
public abstract class BaseConversationResp implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 错误码
     */
    @JsonProperty("errCode")
    protected Integer errCode;

    /**
     * 错误信息
     */
    @JsonProperty("errMsg")
    protected String errMsg;

    /**
     * 详细错误信息
     */
    @JsonProperty("errDlt")
    protected String errDlt;

    public BaseConversationResp() {
        this.errCode = 0;
        this.errMsg = "";
        this.errDlt = "";
    }

    public BaseConversationResp(Integer errCode, String errMsg, String errDlt) {
        this.errCode = errCode;
        this.errMsg = errMsg;
        this.errDlt = errDlt;
    }

    public Integer getErrCode() {
        return errCode;
    }

    public void setErrCode(Integer errCode) {
        this.errCode = errCode;
    }

    public String getErrMsg() {
        return errMsg;
    }

    public void setErrMsg(String errMsg) {
        this.errMsg = errMsg;
    }

    public String getErrDlt() {
        return errDlt;
    }

    public void setErrDlt(String errDlt) {
        this.errDlt = errDlt;
    }

    /**
     * 是否成功
     */
    public boolean isSuccess() {
        return errCode != null && errCode == 0;
    }

    /**
     * 设置成功状态
     */
    protected void setSuccess() {
        this.errCode = 0;
        this.errMsg = "";
        this.errDlt = "";
    }

    /**
     * 设置错误状态
     */
    protected void setError(Integer errCode, String errMsg) {
        this.errCode = errCode;
        this.errMsg = errMsg;
        this.errDlt = "";
    }

    /**
     * 设置错误状态
     */
    protected void setError(Integer errCode, String errMsg, String errDlt) {
        this.errCode = errCode;
        this.errMsg = errMsg;
        this.errDlt = errDlt;
    }

    @Override
    public String toString() {
        return "BaseConversationResp{" +
                "errCode=" + errCode +
                ", errMsg='" + errMsg + '\'' +
                ", errDlt='" + errDlt + '\'' +
                '}';
    }
}
