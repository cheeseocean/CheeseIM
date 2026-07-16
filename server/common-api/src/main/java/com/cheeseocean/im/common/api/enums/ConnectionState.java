package com.cheeseocean.im.common.api.enums;

/**
 * 连接生命周期状态枚举。
 *
 * <p>仅用于单个 postoffice 进程内的连接状态机，不持久化、不上 wire，禁止使用 ordinal。</p>
 *
 * @author xxxcrel
 */
public enum ConnectionState implements IEnum {
    /** 连接已建立，等待认证。 */
    PENDING("待认证"),
    /** 连接已完成认证。 */
    AUTHENTICATED("已认证"),
    /** 连接进入关闭流程。 */
    CLOSING("关闭中"),
    /** 连接已关闭。 */
    CLOSED("已关闭");

    private final String desc;

    ConnectionState(String desc) {
        this.desc = desc;
    }

    @Override
    public String getDesc() {
        return desc;
    }
}
