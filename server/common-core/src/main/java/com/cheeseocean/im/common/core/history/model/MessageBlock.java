package com.cheeseocean.im.common.core.history.model;

import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 历史消息块读模型。
 *
 * <p>该模型属于历史存储端口，不携带 Mongo 注解；集合名、索引和稀疏 map 布局只由 Mongo adapter 管理。</p>
 */
@Data
public class MessageBlock {
    private String id;
    private String conversationId;
    private Long blockNo;
    private Long startSeq;
    private Long endSeq;
    private List<MessageSlot> messages = new ArrayList<>();
    private Instant createdAt;
    private Instant updatedAt;
}
