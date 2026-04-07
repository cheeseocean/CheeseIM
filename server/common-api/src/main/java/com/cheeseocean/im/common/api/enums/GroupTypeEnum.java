package com.cheeseocean.im.common.api.enums;

/**
 * 群组类型枚举。
 *
 * <p>决定群组的规模上限和路由策略：
 * 普通群走 Dubbo 写扩散，超级大群走读扩散（按需拉取）。
 *
 * @author xxxcrel
 */
public enum GroupTypeEnum implements IEnum {

    /** 普通群，成员上限通常几百人，支持写扩散 */
    NORMAL_GROUP(2, "普通群"),
    /** 超级大群，万人以上，采用读扩散模型 */
    SUPER_GROUP(3, "超级大群");

    private final int code;
    private final String desc;

    GroupTypeEnum(int code, String desc) {
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
     * 根据 code 反查枚举，找不到时抛出异常。
     */
    public static GroupTypeEnum fromCode(int code) {
        for (GroupTypeEnum value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("非法群组类型码: " + code);
    }
}
