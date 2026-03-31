package com.cheeseocean.im.common.core.business.mongo.document.group;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 群组 MongoDB 持久化文档。集合：{@code group}。
 * status / groupType / needVerification 存储整数 code。
 */
@Document("group")
@Data
public class GroupDoc {

    @Id
    private String groupId;

    /** 群名称。 */
    @Indexed
    private String groupName;

    /** 群公告内容。 */
    private String notification;
    /** 群简介。 */
    private String introduction;
    /** 群头像地址。 */
    private String faceUrl;
    /** 群扩展信息 JSON。 */
    private String ex;
    /** 群状态编码。 */
    private int    status;
    /** 群创建者用户 ID。 */
    private String creatorUserId;
    /** 群类型编码。 */
    private int    groupType;
    /** 入群验证方式编码。 */
    private int    needVerification;
    /** 是否允许查看成员资料。 */
    private int    lookMemberInfo;
    /** 是否允许加群成员为好友。 */
    private int    applyMemberFriend;
    /** 群公告最近更新时间。 */
    private long   notificationUpdateTime;
    /** 最近更新群公告的用户 ID。 */
    private String notificationUserId;
    /** 群创建时间。 */
    private long   createTime;

}
