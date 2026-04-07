package com.cheeseocean.im.common.api.protocol;

import com.cheeseocean.im.common.api.enums.CommandType;
import lombok.Data;

import java.io.Serializable;

@Data
public class ClientEnvelope implements Serializable {

    private static final long serialVersionUID = 1L;

    private CommandType command;
    private String      requestId;
    private byte[]      body;

}
