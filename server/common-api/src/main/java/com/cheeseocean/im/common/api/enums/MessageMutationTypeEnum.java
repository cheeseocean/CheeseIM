package com.cheeseocean.im.common.api.enums;

/**
 * 消息 overlay 变更类型。
 */
public enum MessageMutationTypeEnum {
    /** 消息已撤回。 */
    REVOKED(1, "已撤回");

    private final int code;
    private final String desc;

    MessageMutationTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /** 根据持久化编码解析类型。 */
    public static MessageMutationTypeEnum fromCode(int code) {
        for (MessageMutationTypeEnum type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown message mutation type code: " + code);
    }
}
