package com.cheeseocean.im.common.api.dto.dispatch;

import com.cheeseocean.im.common.api.dto.message.Message;
import lombok.Data;

import java.io.Serializable;

@Data
public class DispatchPayload implements Serializable {

    private static final long serialVersionUID = 1L;

    private Message msg;

}
