package com.cheeseocean.im.common.api.enums;

/**
 * 用户会话元数据版本日志操作类型。
 */
public enum ConversationVersionOperation {
    /**
     * 会话在用户维度新增或首次可见。
     */
    INSERT,
    /**
     * 会话配置或展示元数据发生变更。
     */
    UPDATE,
    /**
     * 会话在用户维度删除、隐藏或不再可见。
     */
    DELETE
}
