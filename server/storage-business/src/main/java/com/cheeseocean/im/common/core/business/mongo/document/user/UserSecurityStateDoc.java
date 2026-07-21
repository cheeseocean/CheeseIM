package com.cheeseocean.im.common.core.business.mongo.document.user;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 用户安全状态 MongoDB 持久化文档。
 *
 * <p>集合名：{@code user_security_state}，userId 直接作为 _id。
 */
@Document("user_security_state")
@Data
public class UserSecurityStateDoc {

    @Id
    private String userId;
    private long tokenVersion;
    @Indexed
    private boolean banned;
    private long updatedAt;
}
