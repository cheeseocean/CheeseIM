package com.cheeseocean.im.common.api.dto.conversation;

import com.cheeseocean.im.common.api.dto.message.Message;
import lombok.Data;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按 seq 区间拉取消息的响应结果。
 */
@Data
public class PullMessages implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 会话 -> 拉取到的消息列表。
     */
    private Map<String, List<Message>> messagesByConversation = new LinkedHashMap<>();
    /**
     * 会话 -> 当前批次返回的结束 seq。
     */
    private Map<String, Long>          endSeqByConversation = new LinkedHashMap<>();
    /**
     * 会话 -> 是否已经完成当前请求区间。
     */
    private Map<String, Boolean>       completedByConversation = new LinkedHashMap<>();
}
