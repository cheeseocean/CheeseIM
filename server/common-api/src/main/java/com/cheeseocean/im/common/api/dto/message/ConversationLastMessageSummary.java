package com.cheeseocean.im.common.api.dto.message;

import com.cheeseocean.im.common.api.enums.MessagePreviewType;
import lombok.Data;

import java.io.Serializable;

/**
 * 会话列表使用的最新消息摘要。
 *
 * <p>用于承载最近一条消息的展示字段，避免会话查询时回放完整消息体。
 *
 * @author xxxcrel
 */
@Data
public class ConversationLastMessageSummary implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long               seq;
    private String             senderId;
    private String             content;
    private Integer            contentType;
    private String             previewText;
    private MessagePreviewType previewType;
    private Long               sendTime;
    private boolean            notification;
}
