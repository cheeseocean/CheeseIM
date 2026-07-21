package com.cheeseocean.im.common.core.business.mongo.document.user;

import com.cheeseocean.im.common.api.business.domain.User;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 用户基础信息 MongoDB 持久化文档。
 *
 * <p>集合名：{@code user}，userId 直接作为 _id。
 * 与领域对象 {@link User} 通过
 * {@link com.cheeseocean.im.common.core.business.mongo.impl.UserRepositoryImpl} 互相转换。
 */
@Document("user")
@Data
public class UserDoc {

    @Id
    private String userId;

    /**
     * 用户昵称。
     */
    @Indexed
    private String nickname;
    /**
     * 用户头像地址。
     */
    private String avatarUrl;
    /**
     * 用户扩展信息 JSON。
     */
    private String ex;
    /**
     * 应用管理等级。
     */
    private int    appManagerLevel;
    /**
     * 全局免打扰设置，存储整数 code
     * {@link com.cheeseocean.im.common.api.enums.ReceiveOption}
     */
    private int    receiveOpt;
    /**
     * 用户创建时间。
     */
    private long   createTime;

}
