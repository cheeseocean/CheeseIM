package com.cheeseocean.im.apiserver.model.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 增量拉取 mutation 的 HTTP 响应。 */
@Data
public class MessageMutationSyncResponse {

    private List<MessageMutationResponse> mutations = new ArrayList<>();
    private long nextCreatedAt;
    private String nextMutationId;
    private boolean hasMore;
}
