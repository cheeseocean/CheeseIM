package com.cheeseocean.im.common.core.business.mongo.document.group;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 入群申请 MongoDB 持久化文档。集合：{@code group_request}。
 * handleResult 存储整数 code（0=待处理，1=已同意，-1=已拒绝）。
 */
@Document("group_request")
@CompoundIndexes({
        @CompoundIndex(name = "uk_group_request", def = "{'userId': 1, 'groupId': 1}", unique = true),
        @CompoundIndex(name = "idx_group_pending", def = "{'groupId': 1, 'handleResult': 1, 'reqTime': -1}")
})
@Data
public class GroupRequestDoc {

    @Id
    private String id;

    /** 申请人用户 ID。 */
    @Indexed
    private String userId;

    /** 目标群组 ID。 */
    @Indexed
    private String groupId;

    /** 处理结果编码。 */
    private int    handleResult;
    /** 申请附言。 */
    private String reqMsg;
    /** 处理说明。 */
    private String handledMsg;
    /** 实际处理人用户 ID。 */
    private String handleUserId;
    /** 处理时间。 */
    private long   handledTime;
    /** 入群来源编码。 */
    private int    joinSource;
    /** 邀请人用户 ID。 */
    private String inviterUserId;
    /** 扩展信息 JSON。 */
    private String ex;
    /** 申请时间。 */
    private long   reqTime;

}
