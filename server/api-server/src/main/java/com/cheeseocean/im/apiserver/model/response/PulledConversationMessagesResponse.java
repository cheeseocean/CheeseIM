package com.cheeseocean.im.apiserver.model.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 单会话消息拉取结果。
 */
@Data
public class PulledConversationMessagesResponse {

    private String                    conversationId;
    private long                      endSeq;
    private boolean                   completed;
    private List<SyncMessageResponse> messages = new ArrayList<>();
}
