package com.cheeseocean.im.postmaster.history;

import com.cheeseocean.im.common.api.dto.message.MessageOptions;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 消息Slot
 *
 * @author xxxcrel
 */
@Data
public class MessageSlot {

    private Long                seq;
    private String              clientMsgId;
    private String              serverMsgId;
    private String              senderId;
    private String              receiverId;
    private String              groupId;
    private Integer             sessionType;
    private Integer             contentType;
    private byte[]              content;
    private Long                sendTime;
    private Long                createTime;
    private Integer             status;
    private Integer             platformType;
    private String              uniqueId;
    private Integer             source;
    private MessageOptions      options;
    private Map<String, String> attributes = new HashMap<>();
}
