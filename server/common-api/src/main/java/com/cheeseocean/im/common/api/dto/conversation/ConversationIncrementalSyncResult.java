package com.cheeseocean.im.common.api.dto.conversation;

import com.cheeseocean.im.common.api.business.domain.UserConversation;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户会话元数据同步结果。
 *
 * <p>{@code full=true} 时客户端应以 {@link #insert} 全量重建本地会话列表；
 * {@code full=false} 时按 insert/update/delete 增量合并。
 */
@Data
public class ConversationIncrementalSyncResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 服务端当前版本流 ID。
     */
    private String versionId = "";
    /**
     * 服务端当前版本号。
     */
    private long version;
    /**
     * 当前会话 ID 集合 hash。
     */
    private long idHash;
    /**
     * 是否需要客户端全量重建。
     */
    private boolean full;
    /**
     * 新增会话。
     */
    private List<UserConversation> insert = new ArrayList<>();
    /**
     * 更新会话。
     */
    private List<UserConversation> update = new ArrayList<>();
    /**
     * 删除或隐藏的会话 ID。
     */
    private List<String> delete = new ArrayList<>();

    /** readSeq 已变化的会话 ID；客户端据此按需刷新 read snapshot。 */
    private List<String> readStateChangedConversationIds = new ArrayList<>();
}
