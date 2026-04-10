package com.cheeseocean.im.common.core.business.mongo.document.user;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 好友申请 MongoDB 持久化文档。集合：{@code friend_requests}。
 * handleResult 存储整数 code（0=待处理，1=已同意，-1=已拒绝）。
 */
@Document("friend_requests")
@CompoundIndexes({
        @CompoundIndex(name = "uk_friend_request_pair", def = "{'fromUserId': 1, 'toUserId': 1}", unique = true),
        @CompoundIndex(name = "idx_friend_request_incoming", def = "{'toUserId': 1, 'handleResult': 1, 'updatedAt': -1}"),
        @CompoundIndex(name = "idx_friend_request_outgoing", def = "{'fromUserId': 1, 'handleResult': 1, 'updatedAt': -1}")
})
@Data
public class FriendRequestDoc {

    @Id
    private String id;

    @Indexed
    private String fromUserId;
    /**
     * 接收申请的用户 ID。
     */
    @Indexed
    private String toUserId;
    /**
     * 处理结果
     */
    private int    handleResult;
    /**
     * 请求消息
     */
    private String reqMsg;
    /**
     * 处理用户ID
     */
    private String handlerUserId;
    /**
     * 处理说明。
     */
    private String handleMsg;
    /**
     * 处理时间。
     */
    private long   handleTime;
    /**
     * 扩展信息 JSON。
     */
    private String ex;
    /**
     * 申请创建时间。
     */
    private long   createTime;
    /**
     * 最近更新时间。
     */
    private long   updatedAt;

}
