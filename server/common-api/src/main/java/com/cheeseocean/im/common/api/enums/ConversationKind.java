package com.cheeseocean.im.common.api.enums;

/**
 * 会话种类枚举。
 *
 * <p>仅作为 HTTP 展示模型按枚举名称序列化，不是持久化或 wire code，禁止使用 ordinal。</p>
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
