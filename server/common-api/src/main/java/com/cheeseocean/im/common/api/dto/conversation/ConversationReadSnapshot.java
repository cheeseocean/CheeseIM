package com.cheeseocean.im.common.api.dto.conversation;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户视角下的会话读写水位快照。
 */
@Data
public class ConversationReadSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 会话 ID。
     */
    private String conversationId;
    /**
     * 用户已读位点。
     */
    private long   readSeq;
    /**
     * 当前服务端最大消息位点。
     */
    private long   maxSeq;
    /**
     * 当前未读数。
     */
    private long   unreadCount;
}
