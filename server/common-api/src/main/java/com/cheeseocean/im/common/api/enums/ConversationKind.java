package com.cheeseocean.im.common.api.enums;

/**
 * 会话种类枚举。
 *
 * @author xxxcrel
 */
public enum ConversationKind implements IEnum {
    /** 单聊会话。 */
    DIRECT("单聊"),
    /** 群聊会话。 */
    GROUP("群聊"),
    /** 通知会话。 */
    NOTIFICATION("通知"),
    /** 频道会话。 */
    CHANNEL("频道");

    private final String desc;

    ConversationKind(String desc) {
        this.desc = desc;
    }

    @Override
    public String getDesc() {
        return desc;
    }
}
