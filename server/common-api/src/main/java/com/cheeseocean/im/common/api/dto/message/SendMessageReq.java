package com.cheeseocean.im.common.api.dto.message;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

@Data
@AllArgsConstructor
public class SendMessageReq implements Serializable {

    private static final long serialVersionUID = 1L;

    private Message msg;

}
