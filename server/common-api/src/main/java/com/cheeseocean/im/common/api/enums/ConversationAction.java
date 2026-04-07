package com.cheeseocean.im.common.api.enums;

/**
 * 会话相关动作枚举。
 *
 * @author xxxcrel
 */
public enum ConversationAction implements IEnum {
    /** 已读会话。 */
    READ("已读"),
    /** 发送消息。 */
    SEND("发送"),
    /** 撤回消息。 */
    RECALL("撤回"),
    /** 上传附件。 */
    UPLOAD_ATTACHMENT("上传附件");

    private final String desc;

    ConversationAction(String desc) {
        this.desc = desc;
    }

    @Override
    public String getDesc() {
        return desc;
    }
}
