package com.cheeseocean.im.common.core.business.mongo.document.user;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 好友关系 MongoDB 持久化文档。集合：{@code friendships}。
 */
@Document("friendships")
@CompoundIndexes({
        @CompoundIndex(name = "uk_friendship_pair", def = "{'ownerUserId': 1, 'friendUserId': 1}", unique = true)
})
@Data
public class FriendshipDoc {

    @Id
    private String id;

    @Indexed
    private String userId;

    /**
     * 好友用户 ID。
     */
    @Indexed
    private String  friendId;
    /**
     * 好友备注。
     */
    private String  remark;
    /**
     * 加好友来源编码。
     */
    private int     addSource;
    /**
     * 操作者用户 ID。
     */
    private String  operatorId;
    /**
     * 是否置顶该好友关系。
     */
    private boolean isPinned;
    /**
     * 扩展信息 JSON。
     */
    private String  ex;
    /**
     * 创建时间。
     */
    private Long    createdAt;

}
