package com.cheeseocean.im.common.api.enums;

/**
 * 群成员权限等级枚举。
 *
 * <p>权限由低到高：普通成员 < 管理员 < 群主。
 * 存储整数 code，应用逻辑使用 {@link #fromCode(int)} 转换。
 *
 * @author xxxcrel
 */
public enum GroupMemberRoleEnum implements IEnum {

    /** 普通成员 */
    MEMBER(1, "普通成员"),
    /** 群主，拥有全部管理权限 */
    OWNER(2, "群主"),
    /** 管理员，由群主指定 */
    ADMIN(3, "管理员");

    private final int code;
    private final String desc;

    GroupMemberRoleEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 是否为管理员或群主。
     */
    public boolean isAdminOrOwner() {
        return this == ADMIN || this == OWNER;
    }

    /**
     * 根据 code 反查枚举，找不到时抛出异常。
     */
    public static GroupMemberRoleEnum fromCode(int code) {
        for (GroupMemberRoleEnum value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("非法群成员角色码: " + code);
    }
}
