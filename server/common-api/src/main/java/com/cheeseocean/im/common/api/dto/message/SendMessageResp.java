package com.cheeseocean.im.common.api.dto.message;

import lombok.Data;

import java.io.Serializable;

@Data
public class SendMessageResp implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean accepted;
    private String  clientMsgId;
    private String  serverMsgId;
    /** broker 确认接收的服务端时间，不代表接收设备送达。 */
    private long acceptedAt;
    /** 拒绝时返回稳定 {@link com.cheeseocean.im.common.api.enums.ErrorCode}；成功为 0。 */
    private int errorCode;
    /** 面向客户端的简短错误描述，不包含内部异常。 */
    private String errorMessage;
}
