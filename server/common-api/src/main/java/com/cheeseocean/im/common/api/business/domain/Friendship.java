package com.cheeseocean.im.common.api.business.domain;

import lombok.Data;

import java.io.Serializable;

/**
 * 好友关系领域对象。
 *
 * <p>好友关系采用双向写扩散：A-B 互为好友对应两条独立记录（A 视角、B 视角）。
 * 每条记录只对 ownerUserId 可见（如备注、置顶均为私有属性）。
 *
 * @author xxxcrel
 */
@Data
public class Friendship implements Serializable {

    /**
     * 文档唯一标识（"{ownerUserId}:{friendUserId}"）
     */
    private String  id;
    /**
     * 关系所属者（"我"的视角）
     */
    private String  userId;
    /**
     * 好友的用户 ID
     */
    private String  friendId;
    /**
     * 好友备注名（仅对 ownerUserId 可见）
     */
    private String  remark;
    /**
     * 加好友来源（业务方自定义，如 1=搜索，2=扫码，3=群内添加）。
     */
    private int     addSource;
    /**
     * 执行操作的操作者 ID（管理员代操作时与 ownerUserId 不同）
     */
    private String  operatorId;
    /**
     * 是否置顶该好友
     */
    private boolean pinned;
    /**
     * 扩展字段（JSON 字符串）
     */
    private String  ex;
    /**
     * 成为好友的时间（毫秒时间戳）
     */
    private long    createdAt;

}
