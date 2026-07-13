package com.cheeseocean.im.common.api.enums;

/**
 * 用户会话元数据版本日志操作类型。
 */
public enum ConversationVersionOperation {
    /**
     * 会话在用户维度新增或首次可见。
     */
    INSERT(1, "新增会话"),
    /**
     * 会话配置或展示元数据发生变更。
     */
    UPDATE(2, "更新会话"),
    /**
     * 会话在用户维度删除、隐藏或不再可见。
     */
    DELETE(3, "删除会话"),
    /**
     * 会话已读高水位发生推进；客户端收到后应按 conversationId 拉取最新 read snapshot。
     */
    READ_STATE_UPDATED(4, "已读状态更新");

    private final int code;
    private final String desc;

    ConversationVersionOperation(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /** 根据跨进程状态码解析操作类型。 */
    public static ConversationVersionOperation fromCode(int code) {
        for (ConversationVersionOperation operation : values()) {
            if (operation.code == code) {
                return operation;
            }
        }
        throw new IllegalArgumentException("Unknown conversation version operation code: " + code);
    }
}
