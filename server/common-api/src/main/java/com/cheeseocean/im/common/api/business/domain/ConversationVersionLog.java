package com.cheeseocean.im.common.api.business.domain;

import com.cheeseocean.im.common.api.enums.ConversationVersionOperation;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户会话元数据变更日志。
 *
 * <p>该对象记录用户维度会话列表、配置与 readSeq 变化信号。
 * READ_STATE_UPDATED 只提示客户端刷新 read snapshot，不直接携带消息 seq 真相；消息可靠同步
 * 仍由 maxSeq/readSeq 和历史消息拉取承担。
 */
@Data
public class ConversationVersionLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 日志 ID。
     */
    private String id;
    /**
     * 会话所属用户 ID。
     */
    private String ownerUserId;
    /**
     * 当前版本流 ID。版本流重建时会变化，客户端需要转全量同步。
     */
    private String versionId;
    /**
     * 用户维度递增版本号。
     */
    private long version;
    /**
     * 发生变更的会话 ID。
     */
    private String conversationId;
    /**
     * 变更操作。
     */
    private ConversationVersionOperation operation;
    /**
     * 日志创建时间，毫秒时间戳。
     */
    private long createdAt;
}
