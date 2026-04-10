package com.cheeseocean.im.postbox.history;

import com.cheeseocean.im.common.api.dto.message.MessageOptions;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 历史消息槽位文档。
 *
 * @author xxxcrel
 */
@Data
public class MessageSlot {

    private Long                seq;
    private String              clientMsgId;
    private String              serverMsgId;
    private String              senderId;
    private String              senderName;
    private String              receiverId;
    private String              recvId;
    private String              groupId;
    private Integer             sessionType;
    private Integer             contentType;
    private Object              content;
    private Long                sendTime;
    private Long                createTime;
    private Integer             status;
    private Integer             platformType;
    private String              uniqueId;
    private Integer             source;
    private MessageOptions      options = new MessageOptions();
    private Map<String, String> attributes = new HashMap<>();
}
