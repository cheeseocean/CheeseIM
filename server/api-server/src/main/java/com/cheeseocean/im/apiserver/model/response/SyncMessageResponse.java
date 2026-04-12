package com.cheeseocean.im.apiserver.model.response;

import lombok.Data;

import java.util.Map;

/**
 * 同步场景下的消息响应模型。
 */
@Data
public class SyncMessageResponse {

    private Long                seq;
    private String              clientMsgId;
    private String              serverMsgId;
    private String              senderId;
    private String              senderNickName;
    private String              receiverId;
    private String              groupId;
    private Integer             contentType;
    private Integer             sessionType;
    private byte[]              content;
    private Long                sendTime;
    private Long                createTime;
    private Integer             status;
    private Integer             platformType;
    private String              uniqueId;
    private Integer             source;
    private Map<String, String> attributes;
}
