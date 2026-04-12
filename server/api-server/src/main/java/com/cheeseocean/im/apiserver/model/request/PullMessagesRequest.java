package com.cheeseocean.im.apiserver.model.request;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * HTTP 按 seq 范围拉取消息请求。
 */
@Data
public class PullMessagesRequest {

    /**
     * 待拉取的会话区间集合。
     */
    private List<SeqRangeItemRequest> ranges = new ArrayList<>();
    /**
     * 每个会话单次最多返回多少条消息。
     */
    private int limitPerConversation = 100;
}
