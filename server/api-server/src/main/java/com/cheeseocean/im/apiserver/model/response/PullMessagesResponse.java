package com.cheeseocean.im.apiserver.model.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * HTTP 按 seq 范围拉取消息响应。
 */
@Data
public class PullMessagesResponse {

    private List<PulledConversationMessagesResponse> conversations = new ArrayList<>();
}
