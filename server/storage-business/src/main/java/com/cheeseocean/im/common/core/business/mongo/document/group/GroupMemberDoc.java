package com.cheeseocean.im.common.core.business.mongo.document.group;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 群成员 MongoDB 持久化文档。集合：{@code group_member}。
 * roleLevel 存储整数 code（1=成员，2=群主，3=管理员）。
 */
@Document("group_member")
@CompoundIndexes({
        @CompoundIndex(name = "uk_group_member", def = "{'groupId': 1, 'userId': 1}", unique = true),
        @CompoundIndex(name = "idx_group_role", def = "{'groupId': 1, 'roleLevel': 1}"),
        @CompoundIndex(name = "idx_group_join_user", def = "{'groupId': 1, 'joinTime': 1, 'userId': 1}")
})
@Data
public class GroupMemberDoc {

    /**
     * _id = "{groupId}:{userId}"
     */
    @Id
    private String id;

    @Indexed
    private String groupId;

    /**
     * 成员用户 ID。
     */
    @Indexed
    private String userId;

    /**
     * 群内昵称。
     */
    private String nickname;
    /**
     * 群内头像地址。
     */
    private String avatarUrl;
    /**
     * 成员角色等级编码。
     */
    private int    roleLevel;
    /**
     * 入群来源编码。
     */
    private int    joinSource;
    /**
     * 邀请人用户 ID。
     */
    private String inviterUserId;
    /**
     * 操作者用户 ID。
     */
    private String operatorUserId;
    /**
     * 禁言结束时间戳。
     */
    private long   muteEndTime;
    /**
     * 扩展信息 JSON。
     */
    private String ex;
    /**
     * 入群时间。
     */
    private long   joinTime;
}
