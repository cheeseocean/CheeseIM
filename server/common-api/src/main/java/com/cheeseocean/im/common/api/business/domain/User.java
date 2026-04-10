package com.cheeseocean.im.common.api.business.domain;

import com.cheeseocean.im.common.api.enums.ReceiveOption;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户领域对象。
 *
 * <p>表达用户核心业务属性，不依赖任何持久化框架。
 * 全局消息接收设置 {@link #receiveOpt} 与用户身份信息合并存储。
 */
@Data
public class User implements Serializable {

    /**
     * 用户唯一标识
     */
    private String userId;
    /**
     * 用户昵称
     */
    private String nickname;
    /**
     * 头像 URL
     */
    private String avatarUrl;
    /**
     * 业务扩展字段（JSON 字符串）
     */
    private String ex;
    /**
     * 管理员级别。
     * 0=普通用户，1=超管，≥2=通知/系统账号。
     */
    private int    appManagerLevel;
    /**
     * 全局消息接收设置。
     * 0=正常接收，1=不收消息，2=收不提醒。
     * 取值见 {@link ReceiveOption}。
     */
    private int    receiveOpt;
    /**
     * 注册时间（毫秒时间戳）
     */
    private long   createTime;

    // ── 领域方法 ─────────────────────────────────────────────────────────────

    /**
     * 判断当前用户是否具备管理员身份。
     */
    public boolean isAdmin() {
        return appManagerLevel >= 1;
    }

    /**
     * 判断当前用户是否为通知或系统账号。
     */
    public boolean isNotificationAccount() {
        return appManagerLevel >= 2;
    }

}
