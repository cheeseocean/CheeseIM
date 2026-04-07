package com.cheeseocean.im.common.api.enums;

/**
 * 会话列表消息预览类型枚举。
 *
 * @author xxxcrel
 */
public enum MessagePreviewType implements IEnum {
    /** 文本预览。 */
    TEXT("文本"),
    /** 已读回执预览。 */
    READ_RECEIPT("已读回执"),
    /** 撤回消息预览。 */
    REVOKE("撤回"),
    /** 系统消息预览。 */
    SYSTEM("系统"),
    /** 安全类消息预览。 */
    SECURITY("安全"),
    /** 隐藏预览。 */
    HIDDEN("隐藏"),
    /** 通知类预览。 */
    NOTIFICATION("通知");

    private final String desc;

    MessagePreviewType(String desc) {
        this.desc = desc;
    }

    @Override
    public String getDesc() {
        return desc;
    }
}
