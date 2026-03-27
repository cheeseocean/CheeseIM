package com.cheeseocean.im.common.api.conversation;

/**
 * 会话级 recvMsgOpt 设置的 Dubbo 服务接口。
 *
 * <p>取值使用 {@link com.cheeseocean.im.common.core.enums.RecvMsgOpt} 枚举。
 * MongoDB/Kafka/HTTP 传输使用整数 code；应用逻辑中用 {@code RecvMsgOpt.fromCode()} 转换比较。
 */
public interface ConversationRecvOptService {

    /**
     * 返回指定会话的 recvMsgOpt code。
     * 会话记录不存在时返回 {@link com.cheeseocean.im.common.core.enums.RecvMsgOpt#RECEIVE RECEIVE}（0）。
     */
    int getRecvMsgOpt(String ownerUserId, String conversationId);

    /**
     * 持久化指定会话的 recvMsgOpt，会话记录不存在时自动创建。
     *
     * @param recvMsgOpt {@link com.cheeseocean.im.common.core.enums.RecvMsgOpt} 的整数 code
     */
    void setRecvMsgOpt(String ownerUserId, String conversationId, int recvMsgOpt);
}
