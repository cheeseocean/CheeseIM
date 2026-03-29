package com.cheeseocean.im.business.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class UpdateSettingsRequest {

    /** 全局消息接收选项，取值见 {@link com.cheeseocean.im.common.core.enums.RecvMsgOpt}。 */
    @NotNull
    @Min(0)
    @Max(2)
    private Integer globalRecvMsgOpt;

    public Integer getGlobalRecvMsgOpt() {
        return globalRecvMsgOpt;
    }

    public void setGlobalRecvMsgOpt(Integer globalRecvMsgOpt) {
        this.globalRecvMsgOpt = globalRecvMsgOpt;
    }
}
