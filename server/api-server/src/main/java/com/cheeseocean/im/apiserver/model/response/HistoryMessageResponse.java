package com.cheeseocean.im.apiserver.model.response;

import com.cheeseocean.im.common.api.enums.MessagePreviewType;
import lombok.Data;

@Data
public class HistoryMessageResponse {
    private Long sequence;
    private String serverMsgId;
    private String senderId;
    private String senderName;
    private String content;
    private MessagePreviewType previewType;
    private Long sendTime;
}
