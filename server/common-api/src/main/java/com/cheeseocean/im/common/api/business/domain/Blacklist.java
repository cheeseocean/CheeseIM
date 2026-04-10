package com.cheeseocean.im.common.api.business.domain;

import lombok.Data;

/**
 * 黑名单领域对象。
 *
 * <p>代表用户将某人加入黑名单的关系，单向持有（只影响 ownerUserId 一侧）。
 * @author xxxcrel
 */
@Data
public class Blacklist {

    /**
     * 文档唯一标识（"{ownerUserId}:{blockUserId}"）
     */
    private String id;
    /**
     * 执行拉黑操作的用户 ID
     */
    private String ownerUserId;
    /**
     * 被拉黑的用户 ID
     */
    private String blockUserId;
    /**
     * 拉黑来源（业务方自定义，如 1=主动拉黑，2=举报触发）
     */
    private int    addSource;
    /**
     * 执行操作的操作者 ID（管理员代操作时与 ownerUserId 不同）
     */
    private String operatorUserId;
    /**
     * 扩展字段（JSON 字符串）
     */
    private String ex;
    /**
     * 拉黑时间（毫秒时间戳）
     */
    private long   createdAt;

}
