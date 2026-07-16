package com.cheeseocean.im.common.api.enums;

/**
 * 会话鉴权状态枚举。
 *
 * @author xxxcrel
 */
public enum SessionStatus implements IEnum {
    /** 会话有效。 */
    ACTIVE(1, "有效"),
    /** 已主动登出。 */
    LOGOUT(2, "已登出"),
    /** 已吊销。 */
    REVOKED(3, "已吊销"),
    /** 已封禁。 */
    BANNED(4, "已封禁"),
    /** 因密码重置失效。 */
    PASSWORD_RESET_INVALID(5, "密码重置失效"),
    /** 风险锁定。 */
    RISK_LOCKED(6, "风险锁定");

    private final int code;
    private final String desc;

    SessionStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    @Override
    public String getDesc() {
        return desc;
    }

    public static SessionStatus fromCode(int code) {
        for (SessionStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知会话状态码: " + code);
    }
}
