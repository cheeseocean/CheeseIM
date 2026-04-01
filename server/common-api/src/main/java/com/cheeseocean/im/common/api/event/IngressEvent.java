package com.cheeseocean.im.common.api.event;

import com.cheeseocean.im.common.api.dto.message.MessageOptions;
import lombok.Data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 消息入口事件
 *
 * @author xxxcrel
 */
@Data
public class IngressEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String              requestId;
    private String              conversationId;
    private String              clientMsgId;
    private String              serverMsgId;
    private String              senderId;
    private String              receiverId;
    private String              groupId;
    private Integer             sessionType;
    private Integer             contentType;
    private String              content;
    private Long                sendTime;
    private MessageOptions      options;
    private Map<String, String> ext = new HashMap<>();
}
