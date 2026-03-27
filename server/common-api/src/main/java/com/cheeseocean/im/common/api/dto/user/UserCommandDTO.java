package com.cheeseocean.im.common.api.dto.user;

/**
 * 用户自定义命令 DTO。
 * 用于存储用户端的通用 key-value 数据（如收藏、快捷回复等）。
 */
public class UserCommandDTO {

    /** 命令类型，由业务方自定义含义 */
    private int type;

    /** 命令唯一标识 */
    private String uuid;

    /** 命令值（JSON 字符串或普通字符串） */
    private String value;

    /** 扩展字段 */
    private String ex;

    /** 创建时间（毫秒时间戳） */
    private long createTime;

    public int getType() { return type; }
    public void setType(int type) { this.type = type; }

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public String getEx() { return ex; }
    public void setEx(String ex) { this.ex = ex; }

    public long getCreateTime() { return createTime; }
    public void setCreateTime(long createTime) { this.createTime = createTime; }
}
