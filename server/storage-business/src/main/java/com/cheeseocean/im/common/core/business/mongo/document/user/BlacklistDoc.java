package com.cheeseocean.im.common.core.business.mongo.document.user;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 用户黑名单 MongoDB 持久化文档。集合：{@code blacklist}。
 */
@Document("blacklist")
@CompoundIndexes({
        @CompoundIndex(name = "uk_blacklist_pair", def = "{'ownerUserId': 1, 'blockUserId': 1}", unique = true)
})
@Data
public class BlacklistDoc {

    /**
     * _id = "{ownerUserId}:{blockUserId}"
     */
    @Id
    private String id;

    @Indexed
    private String ownerUserId;

    /** 被拉黑用户 ID。 */
    private String blockUserId;
    /** 拉黑来源编码。 */
    private int    addSource;
    /** 操作者用户 ID。 */
    private String operatorUserId;
    /** 扩展信息 JSON。 */
    private String ex;
    /** 创建时间。 */
    private Long   createdAt;

}
