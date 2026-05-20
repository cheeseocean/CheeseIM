package com.cheeseocean.im.apiserver.model.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 会话元数据增量同步 HTTP 响应。
 */
@Data
public class ConversationIncrementalSyncResponse {

    /**
     * 服务端当前会话版本流 ID。
     */
    private String versionId;
    /**
     * 服务端当前会话版本。
     */
    private long version;
    /**
     * 当前会话 ID 集合 hash。
     */
    private long idHash;
    /**
     * 是否要求客户端全量重建会话列表。
     */
    private boolean full;
    /**
     * 新增会话。
     */
    private List<ConversationResponse> insert = new ArrayList<>();
    /**
     * 更新会话。
     */
    private List<ConversationResponse> update = new ArrayList<>();
    /**
     * 删除或隐藏的会话 ID。
     */
    private List<String> delete = new ArrayList<>();
}
