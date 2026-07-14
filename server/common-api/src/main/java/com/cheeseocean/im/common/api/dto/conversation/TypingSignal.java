package com.cheeseocean.im.common.api.dto.conversation;

import com.cheeseocean.im.common.api.enums.TypingActionEnum;
import lombok.Data;

import java.io.Serializable;

/**
 * 已通过会话权限校验的输入中控制信号。
 *
 * <p>该对象只描述在线瞬时状态，禁止用于消息历史、未读数或会话 seq。
 */
@Data
public class TypingSignal implements Serializable {

    private static final long serialVersionUID = 1L;

    private String conversationId;
    private String senderId;
    private TypingActionEnum action;
    private long expiresAt;
}
