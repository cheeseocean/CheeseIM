package com.cheeseocean.im.common.api.enums;

/**
 * 会话鉴权状态枚举。
 *
 * @author xxxcrel
 */
public enum SessionStatus implements IEnum {
    /** 会话有效。 */
    ACTIVE("有效"),
    /** 已主动登出。 */
    LOGOUT("已登出"),
    /** 已吊销。 */
    REVOKED("已吊销"),
    /** 已封禁。 */
    BANNED("已封禁"),
    /** 因密码重置失效。 */
    PASSWORD_RESET_INVALID("密码重置失效"),
    /** 风险锁定。 */
    RISK_LOCKED("风险锁定");

    private final String desc;

    SessionStatus(String desc) {
        this.desc = desc;
    }

    @Override
    public String getDesc() {
        return desc;
    }
}
