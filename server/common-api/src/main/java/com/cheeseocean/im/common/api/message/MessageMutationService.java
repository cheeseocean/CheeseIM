package com.cheeseocean.im.common.api.message;

import com.cheeseocean.im.common.api.dto.message.MessageMutationResult;
import com.cheeseocean.im.common.api.dto.message.MessageMutationSyncResult;

/**
 * 消息变更的唯一共享入口。
 *
 * <p>撤回是历史消息上的 overlay，不修改原始消息块；所有客户端入口都必须经由该接口，
 * 以保证权限、时间窗口和幂等语义一致。
 */
public interface MessageMutationService {

    /**
     * 撤回指定服务端消息 ID 的消息。
     */
    MessageMutationResult revoke(String operatorUserId, String conversationId,
                                 String serverMsgId, String reason);

    /**
     * 按稳定复合游标拉取会话撤回增量。
     */
    MessageMutationSyncResult sync(String userId, String conversationId,
                                   long afterCreatedAt, String afterMutationId, int limit);
}
