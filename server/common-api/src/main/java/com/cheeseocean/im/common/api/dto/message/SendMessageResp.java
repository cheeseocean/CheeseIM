package com.cheeseocean.im.common.api.dto.message;

import lombok.Data;

import java.io.Serializable;

@Data
public class SendMessageResp implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean accepted;
    private String  clientMsgId;
    private String  serverMsgId;
}
