package com.cheeseocean.im.common.api.business.domain;

import lombok.Data;

import java.io.Serializable;

/**
 * 全局会话 seq 边界
 * 会话级序列范围领域对象。
 *
 * <p>仅表达某条会话当前可见的全局消息水位，不携带任何用户维度字段。
 *
 * @author xxxcrel
 */
@Data
public class ConversationRange implements Serializable {

    /**
     * 会话唯一标识
     */
    private String conversationId;
    /**
     * 当前会话已分配的最大消息序列号
     */
    private long   maxSeq;
    /**
     * 当前会话仍可见的最小消息序列号
     */
    private long   minSeq;
}
