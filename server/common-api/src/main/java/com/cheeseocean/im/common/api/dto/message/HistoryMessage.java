package com.cheeseocean.im.common.api.dto.message;

import com.cheeseocean.im.common.api.enums.MessagePreviewType;
import lombok.Data;

import java.io.Serializable;

/**
 * 历史消息查询结果。
 *
 * <p>该 DTO 是历史查询 Dubbo 契约的一部分；HTTP 层应自行转换为响应模型。
 */
@Data
public class HistoryMessage implements Serializable {

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
