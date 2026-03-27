package com.cheeseocean.im.social.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * 用户自定义命令 MongoDB 文档。
 *
 * <p>集合名：{@code user_command}
 * 主键策略："{userId}:{type}:{uuid}"，确保同一用户下 (type, uuid) 组合唯一。
 *
 * <p>用途：存储用户端任意类型的 key-value 扩展数据，
 * type 含义由业务方自定义（如收藏、快捷回复等）。
 */
@Document("user_command")
@CompoundIndexes({
        @CompoundIndex(name = "user_type", def = "{'userId': 1, 'type': 1}")
})
public class UserCommandDocument {

    /** _id = userId:type:uuid */
    @Id
    private String id;

    /** 命令所属用户 ID */
    private String userId;

    /** 命令类型，由业务方自定义含义 */
    private int type;

    /** 命令唯一标识 */
    private String uuid;

    /** 命令值 */
    private String value;

    /** 扩展字段 */
    private String ex;

    /** 创建时间 */
    private Instant createTime;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public int getType() { return type; }
    public void setType(int type) { this.type = type; }

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public String getEx() { return ex; }
    public void setEx(String ex) { this.ex = ex; }

    public Instant getCreateTime() { return createTime; }
    public void setCreateTime(Instant createTime) { this.createTime = createTime; }
}
