package com.cheeseocean.im.apiserver.model.response;

import lombok.Data;

/**
 * 会话读写水位快照响应。
 */
@Data
public class ConversationReadSnapshotResponse {

    private String conversationId;
    private long   readSeq;
    private long   maxSeq;
    private long   unreadCount;
}
