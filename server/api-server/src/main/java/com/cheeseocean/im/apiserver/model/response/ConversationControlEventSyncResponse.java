package com.cheeseocean.im.apiserver.model.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 客户端控制事件游标拉取响应。 */
@Data
public class ConversationControlEventSyncResponse {
    private List<ConversationControlEventResponse> events = new ArrayList<>();
    private long nextCursor;
    private boolean hasMore;
}
