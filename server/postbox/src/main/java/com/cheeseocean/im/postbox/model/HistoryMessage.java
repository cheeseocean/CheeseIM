package com.cheeseocean.im.postbox.model;

import com.cheeseocean.im.common.api.enums.MessagePreviewType;
import lombok.Data;

/**
 * 历史消息查询结果。
 *
 * <p>该模型属于 postbox 内部查询输出，不直接作为 HTTP 响应协议。
 */
@Data
public class HistoryMessage {

    private Long sequence;
    private String serverMsgId;
    private String senderId;
    private String senderName;
    private String content;
    private MessagePreviewType previewType;
    private Long sendTime;
    private boolean revoked;
    private String revokeOperatorUserId;
    private String revokeOperatorName;
    private Long revokedAt;
    private Long mutationVersion;
}
