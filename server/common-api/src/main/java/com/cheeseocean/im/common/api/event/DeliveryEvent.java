package com.cheeseocean.im.common.api.event;

import com.cheeseocean.im.common.api.dto.message.Message;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class DeliveryEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private Message      message;
    private List<String> targetUserIds = new ArrayList<>();
}
