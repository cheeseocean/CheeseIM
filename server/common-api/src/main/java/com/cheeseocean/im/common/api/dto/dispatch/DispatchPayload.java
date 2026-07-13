package com.cheeseocean.im.common.api.dto.dispatch;

import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.protocol.ServerEnvelope;
import lombok.Data;

import java.io.Serializable;

@Data
public class DispatchPayload implements Serializable {

    private static final long serialVersionUID = 1L;

    private Message msg;

    /** 非聊天控制通知；与 msg 二选一。 */
    private ServerEnvelope envelope;

    /** 消息或控制通知的跨节点投递幂等标识。 */
    private String deliveryId;

}
