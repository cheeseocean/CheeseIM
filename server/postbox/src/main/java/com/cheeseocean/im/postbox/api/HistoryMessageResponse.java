package com.cheeseocean.im.postbox.api;

import com.cheeseocean.im.common.api.enums.MessagePreviewType;
import lombok.Data;

/**
 * CheeseBox 历史消息响应。
 *
 * @author xxxcrel
 */
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
